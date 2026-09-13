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
        assertEquals("Enable logging in settings to capture diagnostics."
                        + " Last skipped diagnostic: paused",
                Logger.enableLoggingReminder("paused"));
    }

    @Test
    public void enableLoggingReminderSanitizesMessage() {
        assertEquals("Enable logging in settings to capture diagnostics."
                        + " Last skipped diagnostic: failed hard",
                Logger.enableLoggingReminder(" failed\nhard "));
    }

    @Test
    public void enableLoggingReminderHandlesMissingMessage() {
        assertEquals("Enable logging in settings to capture diagnostics.",
                Logger.enableLoggingReminder(null));
    }
}
