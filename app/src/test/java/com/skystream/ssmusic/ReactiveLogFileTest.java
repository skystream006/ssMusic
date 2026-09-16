package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReactiveLogFileTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder(new File("build"));

    @Test
    public void retainsExactlyOneHundredCompleteMultilineUnicodeMessages() throws Exception {
        File file = folder.newFile("ssmusic.log");
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 135; i++) {
            String entry = message(i);
            ReactiveLogFile.append(file, entry);
            if (i >= 35) {
                expected.append(entry);
            }
        }
        assertEquals(expected.toString(), read(file));
        assertEquals(expected.toString(), Logger.readLogFile(file));
        assertEquals(1, folder.getRoot().list().length);
    }

    @Test
    public void survivesRestartWithoutAnInMemoryIndex() throws Exception {
        File file = folder.newFile("ssmusic.log");
        StringBuilder oldLog = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            oldLog.append(message(i));
        }
        Files.write(file.toPath(), oldLog.toString().getBytes(StandardCharsets.UTF_8));
        ReactiveLogFile.append(new File(file.getPath()), message(100));
        assertEquals(oldLog.substring(message(0).length()) + message(100), read(file));
    }

    @Test
    public void convertsFullLogAndCanReturnToFullThenReactive() throws Exception {
        File file = folder.newFile("ssmusic.log");
        StringBuilder expected = new StringBuilder();
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int i = 0; i < 250; i++) {
                output.write(message(i).getBytes(StandardCharsets.UTF_8));
                if (i >= 150) {
                    expected.append(message(i));
                }
            }
        }
        ReactiveLogFile.append(file, null);
        assertEquals(expected.toString(), read(file));
        // Full mode continues appending ordinary text to the same file.
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(message(250).getBytes(StandardCharsets.UTF_8));
        }
        ReactiveLogFile.append(file, null);
        assertEquals(expected.substring(message(150).length()) + message(250), read(file));
    }

    @Test
    public void clearDoesNotResurrectOldEntries() throws Exception {
        File file = folder.newFile("ssmusic.log");
        ReactiveLogFile.append(file, message(1));
        assertTrue(file.delete());
        ReactiveLogFile.append(file, message(2));
        assertEquals(message(2), read(file));
    }

    @Test
    public void boundsHugeLegacyEntriesAndPreservesUnicodeAtTruncation() throws Exception {
        File file = folder.newFile("ssmusic.log");
        String prefix = "2026-09-16 12:00:00.000 E/Test: ";
        int limit = ReactiveLogFile.MAX_ENTRY_CHARS - ReactiveLogFile.TRUNCATED.length();
        char[] padding = new char[limit - prefix.length() - 1];
        Arrays.fill(padding, '曲');
        String large = prefix + new String(padding) + "🎵" + new String(padding) + "\n";
        Files.write(file.toPath(), large.getBytes(StandardCharsets.UTF_8));
        ReactiveLogFile.append(file, null);
        String bounded = read(file);
        assertTrue(bounded.endsWith(ReactiveLogFile.TRUNCATED));
        assertFalse(bounded.contains("\uFFFD"));
        assertFalse(bounded.contains("?"));
        assertTrue(bounded.length() <= ReactiveLogFile.MAX_ENTRY_CHARS);
        ReactiveLogFile.append(file, null);
        assertEquals(bounded, read(file));
    }

    @Test
    public void capsFileBytesEvenForOneHundredOversizedEntries() throws Exception {
        File file = folder.newFile("ssmusic.log");
        char[] chars = new char[ReactiveLogFile.MAX_ENTRY_CHARS * 2];
        Arrays.fill(chars, '曲');
        String huge = "2026-09-16 12:00:00.000 E/Test: huge\n\t" + new String(chars) + "\n";
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int i = 0; i < 110; i++) {
                output.write(huge.getBytes(StandardCharsets.UTF_8));
            }
        }
        ReactiveLogFile.append(file, null);
        assertTrue(file.length() <= 3L * ReactiveLogFile.MAX_ENTRY_CHARS
                * ReactiveLogFile.MAX_ENTRIES);
        String bounded = read(file);
        assertEquals(100, bounded.split("E/Test:", -1).length - 1);
        assertEquals(100, bounded.split("entry truncated", -1).length - 1);
    }

    @Test
    public void ignoresInterruptedReplacementAndIncompleteFinalMessage() throws Exception {
        File file = folder.newFile("ssmusic.log");
        Files.write(file.toPath(), (message(1) + "2026-09-16 unfinished")
                .getBytes(StandardCharsets.UTF_8));
        File pending = new File(file.getPath() + ReactiveLogFile.PENDING_SUFFIX);
        Files.write(pending.toPath(), message(999).getBytes(StandardCharsets.UTF_8));
        ReactiveLogFile.append(file, message(2));
        assertEquals(message(1) + message(2), read(file));
        assertFalse(pending.exists());
    }

    @Test
    public void emptyNormalizationAndMissingFilesAreSupported() throws Exception {
        File file = new File(folder.getRoot(), "ssmusic.log");
        ReactiveLogFile.append(file, null);
        assertEquals("", read(file));
        ReactiveLogFile.append(file, message(1));
        assertEquals(message(1), read(file));
    }

    private static String message(int number) {
        Throwable cause = new IllegalArgumentException("原因 🎵\n2026-09-16 fake header");
        cause.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("example.Player", "play", "Player.java", number)
        });
        Throwable error = new IllegalStateException("failure " + number, cause);
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("example.Player", "next", "Player.java", number)
        });
        return LogFormat.entry(0L, "E", "Test", "message " + number, error, null);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
