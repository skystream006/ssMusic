package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Test;

public class BuildVersionTest {
    @Test
    public void builtVersionAdvancesWithFirstParentHistory() throws Exception {
        Process process = new ProcessBuilder("git", "rev-list", "--first-parent", "--count",
                "23d6b35ce12bad18592c85b3d6ff48f9e9ba8092..HEAD")
                .redirectErrorStream(true).start();
        String count;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            count = reader.readLine();
        }
        assertEquals("Git history must be available", 0, process.waitFor());
        int increment = Integer.parseInt(count);
        assertEquals(36 + increment, BuildConfig.VERSION_CODE);
        int version = 1002 + increment;
        assertEquals(String.format(Locale.ROOT, "%d.%02d.%02d",
                version / 10000, (version / 100) % 100, version % 100),
                BuildConfig.VERSION_NAME);
        if (increment > 0) {
            assertTrue(BuildConfig.VERSION_CODE > 36);
            assertTrue(UpdatePolicy.compareVersions(BuildConfig.VERSION_NAME, "0.10.02") > 0);
        }
    }
}
