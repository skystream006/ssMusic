package com.skystream.ssmusic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UpdateClientTest {
    private File directory;

    @Before
    public void setUp() throws IOException {
        directory = new File("build/update-client-test-" + UUID.randomUUID());
        Files.createDirectories(directory.toPath());
    }

    @After
    public void tearDown() throws IOException {
        for (File file : directory.listFiles()) Files.delete(file.toPath());
        Files.delete(directory.toPath());
    }

    @Test
    public void copiesExactAndUnknownLengthResponses() throws Exception {
        for (long expected : new long[]{3, -1}) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UpdateClient.copy(stream(), output, 3, expected, () -> {});
            assertArrayEquals(new byte[]{1, 2, 3}, output.toByteArray());
        }
    }

    @Test
    public void rejectsOversizedTruncatedAndExtraBytes() throws Exception {
        for (long[] bounds : new long[][]{{2, -1}, {10, 4}, {10, 2}}) {
            try {
                UpdateClient.copy(stream(), new ByteArrayOutputStream(), bounds[0], bounds[1], () -> {});
                fail("Accepted invalid response size");
            } catch (IOException expected) {
                // Both declared and actual byte counts are bounded.
            }
        }
    }

    @Test
    public void finalizesOnlyCompleteDownload() throws Exception {
        File result = UpdateClient.saveAtomically(stream(), directory, 3, () -> {});
        assertTrue(result.getName().endsWith(".apk"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(result.toPath()));
        assertEquals(1, directory.listFiles().length);
    }

    @Test
    public void truncatedDownloadLeavesNoPartialOrApk() throws Exception {
        try {
            UpdateClient.saveAtomically(stream(), directory, 4, () -> {});
            fail("Accepted truncated APK");
        } catch (IOException expected) {
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void interruptedDownloadLeavesNoPartialOrApk() throws Exception {
        try {
            UpdateClient.saveAtomically(stream(), directory, 3, () -> {
                throw new InterruptedIOException("Cancelled");
            });
            fail("Ignored cancellation");
        } catch (InterruptedIOException expected) {
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void readFailureLeavesNoPartialOrApk() throws Exception {
        try {
            UpdateClient.saveAtomically(new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("Disconnected");
                }
            }, directory, 3, () -> {});
            fail("Ignored disconnection");
        } catch (IOException expected) {
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void cancellationAfterReadPreventsWritingBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int[] checks = {0};
        try {
            UpdateClient.copy(stream(), output, 3, 3, () -> {
                if (++checks[0] == 2) throw new InterruptedIOException();
            });
            fail("Ignored cancellation after read");
        } catch (InterruptedIOException expected) {
            assertEquals(0, output.size());
        }
    }

    @Test
    public void cancelledClientNeverStartsRequest() throws Exception {
        UpdateClient client = new UpdateClient();
        client.cancel();
        try {
            client.latestRelease();
            fail("Started a cancelled request");
        } catch (InterruptedIOException expected) {
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void rejectsUntrustedDownloadBeforeConnecting() throws Exception {
        try {
            new UpdateClient().download("https://example.com/app.apk", 3, directory);
            fail("Accepted unrelated download origin");
        } catch (IOException expected) {
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void rejectsInvalidAssetSizesBeforeConnecting() throws Exception {
        for (long size : new long[]{-1, 0, UpdatePolicy.MAX_APK_BYTES + 1}) {
            try {
                new UpdateClient().download("https://github.com/skystream006/ssMusic/"
                        + "releases/download/v1/app.apk", size, directory);
                fail("Accepted invalid asset size");
            } catch (IOException expected) {
                assertEquals(0, directory.listFiles().length);
            }
        }
    }

    @Test
    public void progressReportsWrittenBytesMonotonicallyAndFinishesAtExpectedSize() throws Exception {
        byte[] data = new byte[32769];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Long> progress = new ArrayList<>();
        UpdateClient.copy(new ByteArrayInputStream(data), output, data.length, data.length,
                () -> {}, (bytes, total) -> {
                    assertEquals(data.length, total);
                    assertEquals(output.size(), bytes);
                    progress.add(bytes);
                });
        assertEquals(Arrays.asList(16384L, 32768L, 32769L), progress);
        assertArrayEquals(data, output.toByteArray());
    }

    @Test
    public void progressAlsoWorksWithoutADeclaredLength() throws Exception {
        List<Long> progress = new ArrayList<>();
        UpdateClient.copy(stream(), new ByteArrayOutputStream(), 3, -1, () -> {},
                (bytes, total) -> {
                    assertEquals(-1, total);
                    progress.add(bytes);
                });
        assertEquals(Arrays.asList(3L), progress);
    }

    @Test
    public void progressDoesNotReportCompletionForTruncatedOrOversizedDownloads() throws Exception {
        for (int expectedSize : new int[]{16384, 32769}) {
            List<Long> progress = new ArrayList<>();
            try {
                UpdateClient.saveAtomically(new ByteArrayInputStream(new byte[16385]),
                        directory, expectedSize, () -> {}, (bytes, total) -> {
                            assertEquals(expectedSize, total);
                            assertTrue(bytes < total);
                            progress.add(bytes);
                        });
                fail("Accepted invalid download");
            } catch (IOException expected) {
                assertEquals(0, directory.listFiles().length);
                assertEquals(expectedSize == 16384 ? Arrays.<Long>asList()
                        : Arrays.asList(16384L, 16385L), progress);
            }
        }
    }

    @Test
    public void cancellationStopsProgressAndDeletesPartialDownload() throws Exception {
        List<Long> progress = new ArrayList<>();
        try {
            UpdateClient.saveAtomically(new ByteArrayInputStream(new byte[32769]),
                    directory, 32769, () -> {
                        if (!progress.isEmpty()) throw new InterruptedIOException("Cancelled");
                    }, (bytes, total) -> progress.add(bytes));
            fail("Ignored cancellation");
        } catch (InterruptedIOException expected) {
            assertEquals(Arrays.asList(16384L), progress);
            assertEquals(0, directory.listFiles().length);
        }
    }

    @Test
    public void failedWritesDoNotAdvanceProgress() throws Exception {
        List<Long> progress = new ArrayList<>();
        OutputStream failingOutput = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("Storage full");
            }
        };
        try {
            UpdateClient.copy(stream(), failingOutput, 3, 3, () -> {},
                    (bytes, total) -> progress.add(bytes));
            fail("Ignored write failure");
        } catch (IOException expected) {
            assertTrue(progress.isEmpty());
        }
    }

    @Test
    public void completedDownloadWithProgressIsStillSavedAtomically() throws Exception {
        List<Long> progress = new ArrayList<>();
        File result = UpdateClient.saveAtomically(stream(), directory, 3, () -> {},
                (bytes, total) -> progress.add(bytes));
        assertEquals(Arrays.asList(3L), progress);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(result.toPath()));
        assertEquals(1, directory.listFiles().length);
    }

    private ByteArrayInputStream stream() {
        return new ByteArrayInputStream(new byte[]{1, 2, 3});
    }
}
