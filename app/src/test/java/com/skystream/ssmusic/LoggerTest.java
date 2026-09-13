package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoggerTest {

    @Test
    public void enableLoggingReminderOnlyAppliesToProblems() {
        assertFalse(Logger.needsEnableLoggingReminder("D"));
        assertFalse(Logger.needsEnableLoggingReminder("I"));
        assertTrue(Logger.needsEnableLoggingReminder("W"));
        assertTrue(Logger.needsEnableLoggingReminder("E"));
    }

    @Test
    public void enableLoggingReminderNamesSuppressedDiagnostic() {
        assertEquals("Enable logging in settings to capture diagnostics: paused",
                Logger.enableLoggingReminder("W", "Playback", "paused"));
    }

    @Test
    public void enableLoggingReminderUsesDefaultTagAndSanitizesMessage() {
        assertEquals("Enable logging in settings to capture diagnostics: failed hard",
                Logger.enableLoggingReminder("E", null, " failed\nhard "));
    }
}
