package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoggerCrashTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder(new File("."));

    private Logger.Mode previousMode;

    @Before
    public void useReactiveMode() throws Exception {
        awaitWriter();
        previousMode = Logger.getMode();
        field("mode").set(null, Logger.Mode.REACTIVE);
    }

    @After
    public void restoreLogger() throws Exception {
        awaitWriter();
        synchronized (field("QUEUE_LOCK").get(null)) {
            synchronized (field("FILE_LOCK").get(null)) {
                field("crashLoggingStarted").setBoolean(null, false);
                field("mode").set(null, previousMode);
            }
        }
    }

    @Test
    public void queuedSealedBatchesCannotEvictSynchronousCrash() throws Exception {
        File file = temporaryFolder.newFile();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Logger.readCurrentLog(null, (text, error) -> {
            started.countDown();
            await(release);
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int batch = 0; batch < 2; batch++) {
                queueBatch(file);
                Logger.readCurrentLog(null, (text, error) -> { });
            }
            Logger.appendCrashToFile(file, "crash\n");
            assertEquals("crash\n", Logger.readLogFile(file));
            Logger.queueReactiveEntry(file, "after crash\n");
        } finally {
            release.countDown();
        }
        awaitWriter();
        assertEquals("crash\n", Logger.readLogFile(file));
    }

    @Test
    public void detachedBatchCannotAppendAfterCrash() throws Exception {
        File file = temporaryFolder.newFile();
        ReactiveLogFile.append(file, "before crash\n");
        Object queueLock = field("QUEUE_LOCK").get(null);
        synchronized (field("FILE_LOCK").get(null)) {
            synchronized (queueLock) {
                queueBatch(file);
            }
            // The worker detaches the batch before blocking on the file lock we hold.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean detached = false;
            while (System.nanoTime() < deadline) {
                synchronized (queueLock) {
                    detached = field("pendingReactiveEntries").get(null) == null;
                }
                if (detached) {
                    break;
                }
                Thread.sleep(1);
            }
            assertTrue("Writer must detach the batch", detached);
            Logger.appendCrashToFile(file, "crash\n");
            assertEquals("before crash\ncrash\n", Logger.readLogFile(file));
        }
        awaitWriter();
        assertEquals("before crash\ncrash\n", Logger.readLogFile(file));
    }

    @Test
    public void crashOnWriterThreadDoesNotWaitForItsOwnQueue() throws Exception {
        File file = temporaryFolder.newFile();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch recorded = new CountDownLatch(1);
        Logger.readCurrentLog(null, (text, error) -> {
            started.countDown();
            await(release);
            Logger.appendCrashToFile(file, "writer crash\n");
            recorded.countDown();
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            queueBatch(file);
        } finally {
            release.countDown();
        }
        assertTrue(recorded.await(5, TimeUnit.SECONDS));
        awaitWriter();
        assertEquals("writer crash\n", Logger.readLogFile(file));
    }

    private static void queueBatch(File file) {
        for (int i = 0; i < 100; i++) {
            Logger.queueReactiveEntry(file, "old message " + i + "\n");
        }
    }

    private static Field field(String name) throws Exception {
        Field field = Logger.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void awaitWriter() throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        Logger.readCurrentLog(null, (text, error) -> finished.countDown());
        assertTrue("Writer must finish without deadlocking", finished.await(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
