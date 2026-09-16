package com.skystream.ssmusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional debug logging. Disabled by default; when the user turns it on from the settings
 * panel every app activity is written to a rotating or recent-message file in the app's
 * private storage and mirrored to logcat.
 */
public final class Logger {

    static final String PREFS_NAME = "ssmusic_prefs";
    static final String KEY_LOGGING_ENABLED = "logging_enabled";
    static final String KEY_LOGGING_MODE = "logging_mode";
    public enum Mode {
        FULL, REACTIVE;

        static Mode fromPreference(String value) {
            return REACTIVE.name().equals(value) ? REACTIVE : FULL;
        }
    }
    static final String LOG_DIRECTORY = "logs";
    static final String LOG_FILE_NAME = "ssmusic.log";
    static final String LOG_BACKUP_FILE_NAME = "ssmusic-previous.log";

    private static final String LOGCAT_TAG = "ssMusic";
    private static final String ENABLE_LOGGING_MESSAGE =
            "Enable logging in settings to capture diagnostics.";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Object FILE_LOCK = new Object();
    private static final Object QUEUE_LOCK = new Object();
    private static Deque<String> pendingReactiveEntries;

    private static volatile Context appContext;
    private static volatile boolean enabled;
    private static volatile Mode mode = Mode.FULL;
    private static final AtomicBoolean enableLoggingReminderShown = new AtomicBoolean();
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
        synchronized (FILE_LOCK) {
            mode = Mode.fromPreference(preferences.getString(KEY_LOGGING_MODE, null));
        }
        prepareReactiveLog();
        installCrashHandler();
        if (enabled) {
            ensureWriter();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Mode getMode() {
        return mode;
    }

    /** Enables the chosen mode; older installations without a mode keep full logging. */
    public static synchronized void enable(Context context, Mode selectedMode) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
        synchronized (FILE_LOCK) {
            mode = selectedMode == null ? Mode.FULL : selectedMode;
        }
        prepareReactiveLog();
        setEnabled(context, true);
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
                    .putString(KEY_LOGGING_MODE, mode.name())
                    .apply();
        }
        if (value && !enabled) {
            enableLoggingReminderShown.set(false);
            enabled = true;
            ensureWriter();
            installCrashHandler();
            event("Logger", "Logging enabled");
        } else if (!value && enabled) {
            event("Logger", "Logging disabled");
            enabled = false;
            enableLoggingReminderShown.set(false);
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

    public interface LogReadCallback {
        void onRead(String text, IOException error);
    }

    /** Reads a snapshot off the UI thread, after entries already queued on the writer. */
    public static synchronized void readCurrentLog(Context context, LogReadCallback callback) {
        File file = logFile(context);
        executeFileOperation(() -> {
            String text = "";
            IOException error = null;
            try {
                text = readLogFile(file);
            } catch (IOException e) {
                error = e;
            }
            callback.onRead(text, error);
        });
    }

    static String readLogFile(File file) throws IOException {
        synchronized (FILE_LOCK) {
            if (file == null || !file.exists()) {
                return "";
            }
            try (InputStreamReader input = new InputStreamReader(new FileInputStream(file), UTF_8)) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    text.append(buffer, 0, count);
                }
                return text.toString();
            }
        }
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
                deleteQuietly(new File(directory, LOG_FILE_NAME + ReactiveLogFile.PENDING_SUFFIX));
            }
        };
        // Delete on the writer thread so entries queued before the clear cannot be written
        // into the new file afterwards.
        try {
            executeFileOperation(delete);
        } catch (RuntimeException e) {
            delete.run();
        }
    }

    private static void write(String level, String tag, String message, Throwable throwable) {
        if (!enabled) {
            logEnableLoggingNeeded(level, tag, message);
            return;
        }
        StackTraceElement[] callerTrace = new Throwable().getStackTrace();
        long timeMillis = System.currentTimeMillis();
        String entry = formatEntry(timeMillis, level, tag, message, throwable, callerTrace);
        logToLogcat(level, tag, message, throwable);
        try {
            if (mode == Mode.REACTIVE) {
                queueReactiveEntry(entry);
            } else {
                executeFileOperation(() -> appendToFile(entry));
            }
        } catch (RuntimeException e) {
            Log.w(LOGCAT_TAG, "Unable to queue log entry", e);
        }
    }

    private static void queueReactiveEntry(String entry) {
        ExecutorService executor = ensureWriter();
        synchronized (QUEUE_LOCK) {
            if (pendingReactiveEntries == null) {
                Deque<String> batch = new ArrayDeque<>(ReactiveLogFile.MAX_ENTRIES);
                pendingReactiveEntries = batch;
                executor.execute(() -> {
                    synchronized (QUEUE_LOCK) {
                        if (pendingReactiveEntries == batch) {
                            pendingReactiveEntries = null;
                        }
                    }
                    for (String pending : batch) {
                        appendToFile(pending);
                    }
                });
            }
            // A slow disk must not build an unbounded queue of superseded diagnostics.
            if (pendingReactiveEntries.size() == ReactiveLogFile.MAX_ENTRIES) {
                pendingReactiveEntries.removeFirst();
            }
            pendingReactiveEntries.addLast(ReactiveLogFile.bound(entry));
        }
    }

    private static void executeFileOperation(Runnable operation) {
        ExecutorService executor = ensureWriter();
        synchronized (QUEUE_LOCK) {
            // Seal the pending batch so later entries cannot cross a read or clear boundary.
            pendingReactiveEntries = null;
            executor.execute(operation);
        }
    }

    private static void logEnableLoggingNeeded(String level, String tag, String message) {
        if (!needsEnableLoggingReminder(level)) {
            return;
        }
        if (!enableLoggingReminderShown.compareAndSet(false, true)) {
            return;
        }
        Log.d(logcatTag(tag), enableLoggingReminder(message));
    }

    static boolean needsEnableLoggingReminder(String level) {
        return "W".equals(level) || "E".equals(level);
    }

    static String enableLoggingReminder(String message) {
        StringBuilder reminder = new StringBuilder(ENABLE_LOGGING_MESSAGE);
        String text = LogFormat.sanitize(message);
        if (!text.isEmpty()) {
            reminder.append(" Last skipped diagnostic: ").append(text);
        }
        return reminder.toString();
    }

    private static void logToLogcat(String level, String tag, String message, Throwable throwable) {
        String logcatTag = logcatTag(tag);
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

    private static String logcatTag(String tag) {
        return tag == null || tag.isEmpty() ? LOGCAT_TAG : LOGCAT_TAG + "/" + tag;
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
            if (mode == Mode.REACTIVE) {
                try {
                    ReactiveLogFile.append(file, entry);
                    deleteQuietly(new File(directory, LOG_BACKUP_FILE_NAME));
                } catch (IOException e) {
                    Log.w(LOGCAT_TAG, "Unable to write reactive log entry", e);
                }
                return;
            }
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

    private static String formatEntry(long timeMillis, String level, String tag, String message,
            Throwable throwable, StackTraceElement[] callerTrace) {
        return mode == Mode.REACTIVE
                ? LogFormat.reactiveEntry(timeMillis, level, tag, message, throwable, callerTrace)
                : LogFormat.entry(timeMillis, level, tag, message, throwable, callerTrace);
    }

    private static void prepareReactiveLog() {
        if (mode == Mode.REACTIVE) {
            // Use the same queue and lock as writes. Queued old-mode entries also obey the
            // current mode, so neither they nor a synchronous crash can undo retention.
            executeFileOperation(() -> {
                synchronized (FILE_LOCK) {
                    if (mode == Mode.REACTIVE) {
                        appendToFile(null);
                    }
                }
            });
        }
    }

    private static ExecutorService ensureWriter() {
        ExecutorService executor = writer;
        if (executor != null) {
            return executor;
        }
        synchronized (Logger.class) {
            // Keep one file queue even when logging is disabled, so reads and clears cannot
            // overtake writes that were queued before the preference changed.
            if (writer == null) {
                writer = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "ssmusic-logger");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return writer;
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
                    String entry = formatEntry(System.currentTimeMillis(), "E", "Crash",
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
