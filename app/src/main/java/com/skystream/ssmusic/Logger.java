package com.skystream.ssmusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional debug logging. Disabled by default; when the user turns it on from the settings
 * panel every app activity is written, with a stack trace, to a rotating file in the app's
 * private storage and mirrored to logcat.
 */
public final class Logger {

    static final String PREFS_NAME = "ssmusic_prefs";
    static final String KEY_LOGGING_ENABLED = "logging_enabled";
    static final String LOG_DIRECTORY = "logs";
    static final String LOG_FILE_NAME = "ssmusic.log";
    static final String LOG_BACKUP_FILE_NAME = "ssmusic-previous.log";

    private static final String LOGCAT_TAG = "ssMusic";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Object FILE_LOCK = new Object();

    private static volatile Context appContext;
    private static volatile boolean enabled;
    private static volatile ExecutorService writer;
    private static boolean crashHandlerInstalled;

    private Logger() {
    }

    /** Reads the stored preference and starts logging when it is turned on. */
    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        SharedPreferences preferences =
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        enabled = preferences.getBoolean(KEY_LOGGING_ENABLED, false);
        installCrashHandler();
        if (enabled) {
            ensureWriter();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Turns logging on or off and remembers the choice. */
    public static synchronized void setEnabled(Context context, boolean value) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (appContext != null) {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LOGGING_ENABLED, value)
                    .apply();
        }
        if (value && !enabled) {
            enabled = true;
            ensureWriter();
            installCrashHandler();
            event("Logger", "Logging enabled");
        } else if (!value && enabled) {
            event("Logger", "Logging disabled");
            enabled = false;
            shutdownWriter();
        }
    }

    public static void event(String tag, String message) {
        write("I", tag, message, null);
    }

    public static void debug(String tag, String message) {
        write("D", tag, message, null);
    }

    public static void warn(String tag, String message, Throwable throwable) {
        write("W", tag, message, throwable);
    }

    public static void error(String tag, String message, Throwable throwable) {
        write("E", tag, message, throwable);
    }

    /** The file the log is written to; may not exist yet. */
    public static File logFile(Context context) {
        Context target = context == null ? appContext : context.getApplicationContext();
        if (target == null) {
            return null;
        }
        return new File(new File(target.getFilesDir(), LOG_DIRECTORY), LOG_FILE_NAME);
    }

    public static boolean hasContent(Context context) {
        File file = logFile(context);
        return file != null && file.isFile() && file.length() > 0L;
    }

    /**
     * Deletes the current log and its backup. The deletion is queued behind pending writes,
     * so it may complete shortly after this call returns.
     */
    public static void clear(Context context) {
        Context target = context == null ? appContext : context.getApplicationContext();
        if (target == null) {
            return;
        }
        File directory = new File(target.getFilesDir(), LOG_DIRECTORY);
        Runnable delete = () -> {
            synchronized (FILE_LOCK) {
                deleteQuietly(new File(directory, LOG_FILE_NAME));
                deleteQuietly(new File(directory, LOG_BACKUP_FILE_NAME));
            }
        };
        // Delete on the writer thread so entries queued before the clear cannot be written
        // into the new file afterwards.
        ExecutorService executor = enabled ? ensureWriter() : writer;
        if (executor == null) {
            delete.run();
            return;
        }
        try {
            executor.execute(delete);
        } catch (RuntimeException e) {
            delete.run();
        }
    }

    private static void write(String level, String tag, String message, Throwable throwable) {
        if (!enabled) {
            return;
        }
        StackTraceElement[] callerTrace = new Throwable().getStackTrace();
        long timeMillis = System.currentTimeMillis();
        String entry = LogFormat.entry(timeMillis, level, tag, message, throwable, callerTrace);
        logToLogcat(level, tag, message, throwable);
        ExecutorService executor = ensureWriter();
        if (executor == null) {
            return;
        }
        try {
            executor.execute(() -> appendToFile(entry));
        } catch (RuntimeException e) {
            Log.w(LOGCAT_TAG, "Unable to queue log entry", e);
        }
    }

    private static void logToLogcat(String level, String tag, String message, Throwable throwable) {
        String logcatTag = tag == null || tag.isEmpty() ? LOGCAT_TAG : LOGCAT_TAG + "/" + tag;
        String text = LogFormat.sanitize(message);
        if ("E".equals(level)) {
            Log.e(logcatTag, text, throwable);
        } else if ("W".equals(level)) {
            Log.w(logcatTag, text, throwable);
        } else if ("D".equals(level)) {
            Log.d(logcatTag, text, throwable);
        } else {
            Log.i(logcatTag, text, throwable);
        }
    }

    private static void appendToFile(String entry) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        synchronized (FILE_LOCK) {
            File directory = new File(context.getFilesDir(), LOG_DIRECTORY);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            File file = new File(directory, LOG_FILE_NAME);
            if (file.length() > LogFormat.MAX_FILE_BYTES) {
                File backup = new File(directory, LOG_BACKUP_FILE_NAME);
                deleteQuietly(backup);
                if (!file.renameTo(backup)) {
                    deleteQuietly(file);
                }
            }
            try (Writer output = new OutputStreamWriter(
                    new FileOutputStream(file, true), UTF_8)) {
                output.write(entry);
            } catch (IOException e) {
                Log.w(LOGCAT_TAG, "Unable to write log entry", e);
            }
        }
    }

    private static ExecutorService ensureWriter() {
        ExecutorService executor = writer;
        if (executor != null) {
            return executor;
        }
        synchronized (Logger.class) {
            // Re-check under the lock so a concurrent disable cannot be undone by a log call
            // that passed the enabled check just before the writer was shut down.
            if (writer == null && enabled) {
                writer = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "ssmusic-logger");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return writer;
        }
    }

    private static void shutdownWriter() {
        ExecutorService executor;
        synchronized (Logger.class) {
            executor = writer;
            writer = null;
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    private static void installCrashHandler() {
        if (crashHandlerInstalled) {
            return;
        }
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                if (enabled) {
                    String entry = LogFormat.entry(System.currentTimeMillis(), "E", "Crash",
                            "Uncaught exception on thread " + thread.getName(), throwable, null);
                    appendToFile(entry);
                }
            } catch (RuntimeException e) {
                Log.w(LOGCAT_TAG, "Unable to record crash", e);
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        crashHandlerInstalled = true;
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            Log.w(LOGCAT_TAG, "Unable to delete " + file.getName());
        }
    }
}
