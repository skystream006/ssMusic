package com.skystream.ssmusic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

final class StatsValues {

    static final long UNAVAILABLE = -1L;

    private StatsValues() {
    }

    static long bytesPerSecond(long previous, long current, long elapsedMillis) {
        if (previous < 0 || current < previous || elapsedMillis <= 0) {
            return UNAVAILABLE;
        }
        return Math.round((current - previous) * (1000.0 / elapsedMillis));
    }

    static final class NetworkRates {
        final long download;
        final long upload;

        NetworkRates(long download, long upload) {
            this.download = download;
            this.upload = upload;
        }
    }

    static final class NetworkTracker {
        private long previousRx = UNAVAILABLE;
        private long previousTx = UNAVAILABLE;
        private long previousTime = UNAVAILABLE;

        NetworkRates sample(long rx, long tx, long elapsedRealtime) {
            long elapsed = previousTime >= 0 && elapsedRealtime >= previousTime
                    ? elapsedRealtime - previousTime : 0;
            NetworkRates rates = new NetworkRates(
                    bytesPerSecond(previousRx, rx, elapsed),
                    bytesPerSecond(previousTx, tx, elapsed));
            previousRx = rx;
            previousTx = tx;
            previousTime = elapsedRealtime;
            return rates;
        }
    }

    static long storageBytes(File... roots) {
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (File root : roots) {
            if (root != null) {
                pending.add(root);
            }
        }
        long total = 0;
        try {
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    return UNAVAILABLE;
                }
                File file = pending.removeLast().getAbsoluteFile();
                // Resolve parent aliases (e.g. /sdcard), but never follow a linked entry.
                File parent = file.getParentFile();
                File entry = parent == null ? file
                        : new File(parent.getCanonicalFile(), file.getName());
                File canonical = entry.getCanonicalFile();
                if (!entry.equals(canonical) || !visited.add(canonical.getPath())
                        || !canonical.exists()) {
                    continue;
                }
                if (!canonical.canRead()) {
                    return UNAVAILABLE;
                }
                if (canonical.isDirectory()) {
                    File[] children = canonical.listFiles();
                    if (children == null) {
                        return UNAVAILABLE;
                    }
                    for (File child : children) {
                        pending.add(child);
                    }
                } else if (canonical.isFile()) {
                    long size = canonical.length();
                    if (size > Long.MAX_VALUE - total) {
                        return UNAVAILABLE;
                    }
                    total += size;
                }
            }
            return total;
        } catch (IOException | SecurityException e) {
            return UNAVAILABLE;
        }
    }
}
