package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoggerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void absentAndUnknownModePreferencesKeepFullLogging() {
        assertEquals(Logger.Mode.FULL, Logger.Mode.fromPreference(null));
        assertEquals(Logger.Mode.FULL, Logger.Mode.fromPreference(""));
        assertEquals(Logger.Mode.FULL, Logger.Mode.fromPreference("future-mode"));
        assertEquals(Logger.Mode.FULL, Logger.Mode.fromPreference(Logger.Mode.FULL.name()));
        assertEquals(Logger.Mode.REACTIVE, Logger.Mode.fromPreference(Logger.Mode.REACTIVE.name()));
    }

    @Test
    public void currentLogHandlesMissingAndEmptyFiles() throws IOException {
        assertEquals("", Logger.readLogFile(null));
        assertEquals("", Logger.readLogFile(new File(temporaryFolder.getRoot(), "missing.log")));
        assertEquals("", Logger.readLogFile(temporaryFolder.newFile()));
    }

    @Test
    public void currentLogPreservesMultilineUnicodeText() throws IOException {
        File file = temporaryFolder.newFile();
        String entry = "Media player swipe up: up next control clicked\n曲名 🎵\n    stack trace\n";
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(entry.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(entry, Logger.readLogFile(file));
    }

    @Test(expected = IOException.class)
    public void currentLogReportsReadErrors() throws IOException {
        Logger.readLogFile(temporaryFolder.getRoot());
    }

    @Test
    public void snapshotsStayOnTheSameFileQueueWhileLoggingIsDisabled() throws InterruptedException {
        assertFalse(Logger.isEnabled());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        Logger.readCurrentLog(null, (text, error) -> {
            firstStarted.countDown();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            Logger.readCurrentLog(null, (text, error) -> secondFinished.countDown());
            assertFalse(secondFinished.await(100, TimeUnit.MILLISECONDS));
        } finally {
            releaseFirst.countDown();
        }
        assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
        assertFalse(Logger.isEnabled());
    }

    @Test
    public void reactiveBacklogIsBoundedAndSnapshotsSealBatches() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        Logger.readCurrentLog(null, (text, error) -> {
            firstStarted.countDown();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            Method enqueue = Logger.class.getDeclaredMethod("queueReactiveEntry", String.class);
            enqueue.setAccessible(true);
            Field pending = Logger.class.getDeclaredField("pendingReactiveEntries");
            pending.setAccessible(true);
            for (int i = 0; i < 135; i++) {
                enqueue.invoke(null, "message " + i + "\n");
            }
            Deque<?> batch = (Deque<?>) pending.get(null);
            assertEquals(100, batch.size());
            assertEquals("message 35\n", batch.peekFirst());
            assertEquals("message 134\n", batch.peekLast());
            Logger.readCurrentLog(null, (text, error) -> { });
            enqueue.invoke(null, "after snapshot\n");
            assertEquals(100, batch.size());
            assertEquals("after snapshot\n", ((Deque<?>) pending.get(null)).peekFirst());
            Logger.readCurrentLog(null, (text, error) -> finished.countDown());
        } finally {
            releaseFirst.countDown();
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void gestureDiagnosticsAreSilentWhenLoggingIsDisabled() {
        assertFalse(Logger.isEnabled());
        Logger.debug("MainActivity",
                "Bridge diagnostic: Media player swipe left: previous control clicked");
        assertFalse(Logger.isEnabled());
    }

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
