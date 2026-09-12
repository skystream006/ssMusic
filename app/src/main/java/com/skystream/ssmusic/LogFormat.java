package com.skystream.ssmusic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Formatting rules for the debug log. Kept free of Android dependencies so it can be unit
 * tested on the JVM.
 */
public final class LogFormat {

    /** Maximum size of the active log file before it is rotated to a single backup file. */
    public static final long MAX_FILE_BYTES = 512L * 1024L;

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_STACK_FRAMES = 60;
    private static final String PACKAGE_PREFIX = "com.skystream.ssmusic.";
    // SimpleDateFormat is not thread safe, so each logging thread keeps its own instance.
    private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                }
            };
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String LOGGER_CLASS = PACKAGE_PREFIX + "Logger";
    private static final String FORMAT_CLASS = PACKAGE_PREFIX + "LogFormat";

    private LogFormat() {
    }

    /**
     * Builds a single log entry. Every entry names the calling code; a full stack trace is
     * recorded for the supplied throwable, and for warnings and errors the caller's own trace
     * is recorded when no throwable is available.
     */
    public static String entry(long timeMillis, String level, String tag, String message,
            Throwable throwable, StackTraceElement[] callerTrace) {
        StringBuilder line = new StringBuilder();
        line.append(timestamp(timeMillis))
                .append(' ')
                .append(level == null || level.isEmpty() ? "I" : level)
                .append('/')
                .append(tag == null || tag.isEmpty() ? "ssMusic" : tag)
                .append(": ")
                .append(sanitize(message));
        String caller = callerLocation(callerTrace);
        if (caller != null) {
            line.append(" (at ").append(caller).append(')');
        }
        line.append('\n');
        String trace = throwable != null
                ? stackTraceOf(throwable)
                : (tracesCallerFor(level) ? stackTraceOf(callerTrace) : null);
        if (trace != null && !trace.isEmpty()) {
            line.append(trace);
            if (!trace.endsWith("\n")) {
                line.append('\n');
            }
        }
        return line.toString();
    }

    /** Levels whose entries carry the caller's stack trace even without a throwable. */
    public static boolean tracesCallerFor(String level) {
        return "W".equals(level) || "E".equals(level);
    }

    public static String timestamp(long timeMillis) {
        SimpleDateFormat format = TIMESTAMP_FORMAT.get();
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(timeMillis));
    }

    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String single = WHITESPACE.matcher(message).replaceAll(" ").trim();
        return single.length() <= MAX_MESSAGE_LENGTH
                ? single : single.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    /** Returns the first application frame outside the logging classes, or {@code null}. */
    public static String callerLocation(StackTraceElement[] trace) {
        if (trace == null) {
            return null;
        }
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            if (className.startsWith(PACKAGE_PREFIX)
                    && !className.equals(LOGGER_CLASS)
                    && !className.equals(FORMAT_CLASS)) {
                return simpleName(className) + "." + element.getMethodName()
                        + ":" + element.getLineNumber();
            }
        }
        return null;
    }

    public static String stackTraceOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return indent(writer.toString());
    }

    public static String stackTraceOf(StackTraceElement[] trace) {
        if (trace == null || trace.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (StackTraceElement element : trace) {
            if (element.getClassName().equals(LOGGER_CLASS)
                    || element.getClassName().equals(FORMAT_CLASS)) {
                continue;
            }
            builder.append("\tat ").append(element).append('\n');
            if (++written >= MAX_STACK_FRAMES) {
                builder.append("\t... trace truncated\n");
                break;
            }
        }
        return builder.toString();
    }

    private static String indent(String trace) {
        StringBuilder builder = new StringBuilder();
        for (String line : trace.split("\n")) {
            String trimmed = line.replace("\r", "");
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("\t")) {
                builder.append('\t');
            }
            builder.append(trimmed).append('\n');
        }
        return builder.toString();
    }

    private static String simpleName(String className) {
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }
}
