package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogFormatTest {

    @Test
    public void entryContainsLevelTagAndMessage() {
        String entry = LogFormat.entry(0L, "W", "Playback", "paused", null, null);
        assertTrue(entry.contains("W/Playback: paused"));
        assertTrue(entry.endsWith("\n"));
    }

    @Test
    public void entryRecordsThrowableStackTrace() {
        Throwable throwable = new IllegalStateException("boom");
        String entry = LogFormat.entry(0L, "E", "Playback", "failed", throwable, null);
        assertTrue(entry.contains("java.lang.IllegalStateException: boom"));
        assertTrue(entry.contains("\tat "));
    }

    @Test
    public void entryRecordsCallerStackTraceForWarnings() {
        String entry = LogFormat.entry(0L, "W", "Nav", "home",
                null, new Throwable().getStackTrace());
        assertTrue(entry.contains("(at LogFormatTest.entryRecordsCallerStackTraceForWarnings:"));
        assertTrue(entry.contains("\tat com.skystream.ssmusic.LogFormatTest"));
    }

    @Test
    public void entryKeepsCallerLocationWithoutFullTraceForRoutineEvents() {
        String entry = LogFormat.entry(0L, "I", "Nav", "home",
                null, new Throwable().getStackTrace());
        assertTrue(entry.contains(
                "(at LogFormatTest.entryKeepsCallerLocationWithoutFullTraceForRoutineEvents:"));
        assertFalse(entry.contains("\tat "));
    }

    @Test
    public void usesDefaultsForMissingLevelAndTag() {
        assertTrue(LogFormat.entry(0L, null, null, "message", null, null)
                .contains("I/ssMusic: message"));
    }

    @Test
    public void sanitizeCollapsesNewlinesAndTrims() {
        assertEquals("a b", LogFormat.sanitize(" a\r\nb "));
        assertEquals("", LogFormat.sanitize(null));
    }

    @Test
    public void sanitizeTruncatesVeryLongMessages() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            builder.append('x');
        }
        String sanitized = LogFormat.sanitize(builder.toString());
        assertEquals(4001, sanitized.length());
        assertTrue(sanitized.endsWith("…"));
    }

    @Test
    public void callerLocationSkipsLoggingClasses() {
        StackTraceElement[] trace = {
                new StackTraceElement("com.skystream.ssmusic.Logger", "event", "Logger.java", 10),
                new StackTraceElement("com.skystream.ssmusic.LogFormat", "entry", "LogFormat.java", 20),
                new StackTraceElement("com.skystream.ssmusic.MainActivity", "onCreate",
                        "MainActivity.java", 30),
        };
        assertEquals("MainActivity.onCreate:30", LogFormat.callerLocation(trace));
    }

    @Test
    public void callerLocationIgnoresFramesOutsideTheApp() {
        StackTraceElement[] trace = {
                new StackTraceElement("android.app.Activity", "performCreate", "Activity.java", 1),
        };
        assertNull(LogFormat.callerLocation(trace));
        assertNull(LogFormat.callerLocation(null));
    }

    @Test
    public void timestampIsHumanReadable() {
        assertTrue(LogFormat.timestamp(0L)
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
    }
}
