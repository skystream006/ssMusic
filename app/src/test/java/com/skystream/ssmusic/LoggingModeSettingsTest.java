package com.skystream.ssmusic;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class LoggingModeSettingsTest {
    @Test
    public void switchStaysOffUntilSelectionAndCancelDoesNotEnableLogging() throws Exception {
        String source = source("MainActivity");
        String listener = source.substring(source.indexOf("Switch loggingSwitch"),
                source.indexOf("R.id.share_log_button"));
        assertTrue(listener.contains("if (checked == Logger.isEnabled())"));
        assertTrue(listener.indexOf("loggingSwitch.setChecked(false)")
                < listener.indexOf(".setItems(R.array.logging_modes"));
        assertTrue(listener.contains("which == 1 ? Logger.Mode.REACTIVE : Logger.Mode.FULL"));
        assertTrue(listener.contains(".setNegativeButton(android.R.string.cancel, null)"));
        assertTrue(listener.contains("Logger.setEnabled(MainActivity.this, false)"));
    }

    @Test
    public void modeAndEnabledStateArePersistedTogetherAndRestored() throws Exception {
        String source = source("Logger");
        assertTrue(source.contains("preferences.getBoolean(KEY_LOGGING_ENABLED, false)"));
        assertTrue(source.contains("Mode.fromPreference(preferences.getString(KEY_LOGGING_MODE, null))"));
        assertTrue(source.contains(".putBoolean(KEY_LOGGING_ENABLED, value)\n"
                + "                    .putString(KEY_LOGGING_MODE, mode.name())"));
        assertTrue(source.contains("if (file.length() > LogFormat.MAX_FILE_BYTES)"));
        assertTrue(source.contains("new FileOutputStream(file, true)"));
        assertTrue(source.contains("appendToFile(entry);"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssmusic/" + name + ".java")), StandardCharsets.UTF_8);
    }
}
