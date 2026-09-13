package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StatsValuesTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void ratesUseElapsedTimeInsteadOfAssumingOneSecond() {
        assertEquals(200L, StatsValues.bytesPerSecond(100L, 400L, 1500L));
        assertEquals(600L, StatsValues.bytesPerSecond(100L, 400L, 500L));
        assertEquals(0L, StatsValues.bytesPerSecond(100L, 100L, 1000L));
    }

    @Test
    public void invalidCountersResetsAndNonpositiveTimeAreUnavailable() {
        assertEquals(-1L, StatsValues.bytesPerSecond(-1L, 100L, 1000L));
        assertEquals(-1L, StatsValues.bytesPerSecond(100L, -1L, 1000L));
        assertEquals(-1L, StatsValues.bytesPerSecond(100L, 99L, 1000L));
        assertEquals(-1L, StatsValues.bytesPerSecond(100L, 200L, 0L));
        assertEquals(-1L, StatsValues.bytesPerSecond(100L, 200L, -1000L));
    }

    @Test
    public void largeRatesDoNotOverflowToNegativeValues() {
        assertEquals(Long.MAX_VALUE, StatsValues.bytesPerSecond(0L, Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, StatsValues.bytesPerSecond(0L, Long.MAX_VALUE, 1000L));
    }

    @Test
    public void firstSampleIsUnavailableAndDirectionsAreIndependent() {
        StatsValues.NetworkTracker tracker = new StatsValues.NetworkTracker();
        assertRates(-1L, -1L, tracker.sample(100L, -1L, 1000L));
        assertRates(200L, -1L, tracker.sample(300L, 400L, 2000L));
        assertRates(0L, 100L, tracker.sample(300L, 500L, 3000L));
    }

    @Test
    public void resetAndUnsupportedCountersNeedANewBaseline() {
        StatsValues.NetworkTracker tracker = new StatsValues.NetworkTracker();
        tracker.sample(100L, 100L, 1000L);
        assertRates(-1L, -1L, tracker.sample(0L, -1L, 2000L));
        assertRates(200L, -1L, tracker.sample(200L, 200L, 3000L));
        assertRates(0L, 0L, tracker.sample(200L, 200L, 4000L));
    }

    @Test
    public void repeatedOrReversedClockSamplesDoNotGenerateRates() {
        StatsValues.NetworkTracker tracker = new StatsValues.NetworkTracker();
        tracker.sample(100L, 100L, 1000L);
        assertRates(-1L, -1L, tracker.sample(200L, 200L, 1000L));
        assertRates(-1L, -1L, tracker.sample(300L, 300L, 500L));
        assertRates(100L, 100L, tracker.sample(400L, 400L, 1500L));
    }

    @Test
    public void newTrackerDoesNotReusePreviousSessionCounters() {
        StatsValues.NetworkTracker old = new StatsValues.NetworkTracker();
        old.sample(100L, 100L, 1000L);
        assertRates(-1L, -1L, new StatsValues.NetworkTracker().sample(900L, 900L, 9000L));
    }

    @Test
    public void storageCountsNestedDataCacheAndExternalRootsOnce() throws IOException {
        File data = folder.newFolder("data");
        File cache = new File(data, "cache");
        Files.createDirectory(cache.toPath());
        File webview = new File(data, "app_webview");
        Files.createDirectory(webview.toPath());
        File external = folder.newFolder("external");
        File file = writeBytes(data, "settings", 3);
        writeBytes(cache, "cached", 7);
        writeBytes(webview, "database", 11);
        writeBytes(external, "download", 13);
        assertEquals(34L, StatsValues.storageBytes(data, cache, webview, external, data, file));
        assertEquals(34L, StatsValues.storageBytes(file, cache, webview, external, data));
    }

    @Test
    public void missingAndNullRootsAndEmptyDirectoriesAreSkipped() throws IOException {
        assertEquals(0L, StatsValues.storageBytes(
                null, new File(folder.getRoot(), "missing"), folder.newFolder("empty")));
        assertEquals(0L, StatsValues.storageBytes());
    }

    @Test
    public void symlinkFilesDirectoriesAndCyclesAreExcluded() throws IOException {
        File data = folder.newFolder("data");
        File outside = folder.newFolder("outside");
        File original = writeBytes(data, "original", 5);
        writeBytes(outside, "excluded", 19);
        Files.createSymbolicLink(new File(data, "linked-file").toPath(), original.toPath());
        Files.createSymbolicLink(new File(data, "linked-dir").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(data, "cycle").toPath(), data.toPath());
        File linkedRoot = new File(folder.getRoot(), "linked-root");
        Files.createSymbolicLink(linkedRoot.toPath(), outside.toPath());
        assertEquals(5L, StatsValues.storageBytes(data, linkedRoot));
    }

    @Test
    public void ancestorAliasesDoNotDoubleCountStorage() throws IOException {
        File data = folder.newFolder("data");
        File cache = new File(data, "cache");
        Files.createDirectory(cache.toPath());
        writeBytes(cache, "cached", 7);
        File alias = new File(folder.getRoot(), "alias");
        Files.createSymbolicLink(alias.toPath(), data.toPath());
        assertEquals(7L, StatsValues.storageBytes(new File(alias, "cache"), cache, data));
    }

    @Test
    public void cancelledStorageScanReturnsUnavailable() throws IOException {
        File data = folder.newFolder("data");
        Thread.currentThread().interrupt();
        try {
            assertEquals(-1L, StatsValues.storageBytes(data));
        } finally {
            Thread.interrupted();
        }
    }

    private static File writeBytes(File parent, String name, int count) throws IOException {
        File file = new File(parent, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[count]);
        }
        return file;
    }

    private static void assertRates(long download, long upload, StatsValues.NetworkRates rates) {
        assertEquals(download, rates.download);
        assertEquals(upload, rates.upload);
    }
}
