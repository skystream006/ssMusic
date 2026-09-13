package com.skystream.ssmusic;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class StatsMonitor {

    private static final long STORAGE_REFRESH_MS = 30_000L;

    private final Context context;
    private TextView output;
    private boolean enabled;
    private boolean started;
    private boolean destroyed;
    private SamplingSession session;

    StatsMonitor(Context context, TextView output) {
        this.context = context.getApplicationContext();
        this.output = output;
    }

    synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateSampling();
    }

    synchronized void onStart() {
        started = true;
        updateSampling();
    }

    synchronized void onStop() {
        started = false;
        updateSampling();
    }

    synchronized void destroy() {
        destroyed = true;
        updateSampling();
        output = null;
    }

    private void updateSampling() {
        if (enabled && started && !destroyed) {
            if (session == null) {
                output.setText(R.string.stats_loading);
                session = new SamplingSession();
                session.executor.scheduleWithFixedDelay(session::sample, 0, 1, TimeUnit.SECONDS);
            }
        } else if (session != null) {
            SamplingSession previous = session;
            session = null;
            previous.executor.shutdownNow();
            previous.handler.removeCallbacksAndMessages(null);
        }
    }

    private long memoryBytes() {
        try {
            Debug.MemoryInfo memory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memory);
            long pss = memory.getTotalPss();
            return pss >= 0 ? pss * 1024L : StatsValues.UNAVAILABLE;
        } catch (RuntimeException e) {
            return StatsValues.UNAVAILABLE;
        }
    }

    private long storageBytes() {
        try {
            List<File> roots = new ArrayList<>();
            roots.add(new File(context.getApplicationInfo().dataDir));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                roots.add(context.createDeviceProtectedStorageContext().getDataDir());
            }
            File[] externalFiles = context.getExternalFilesDirs(null);
            File[] externalCache = context.getExternalCacheDirs();
            if (externalFiles != null) {
                Collections.addAll(roots, externalFiles);
            }
            if (externalCache != null) {
                Collections.addAll(roots, externalCache);
            }
            return StatsValues.storageBytes(roots.toArray(new File[0]));
        } catch (RuntimeException e) {
            return StatsValues.UNAVAILABLE;
        }
    }

    private String formatBytes(long bytes) {
        return bytes < 0 ? context.getString(R.string.stats_unavailable)
                : Formatter.formatShortFileSize(context, bytes);
    }

    private String formatRate(long bytes) {
        return bytes < 0 ? context.getString(R.string.stats_unavailable)
                : context.getString(R.string.stats_bytes_per_second, formatBytes(bytes));
    }

    private final class SamplingSession {
        final Handler handler = new Handler(Looper.getMainLooper());
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final StatsValues.NetworkTracker network = new StatsValues.NetworkTracker();
        long storage = StatsValues.UNAVAILABLE;
        long lastStorageRefresh = -1L;

        void sample() {
            long memory = memoryBytes();
            long rx = StatsValues.UNAVAILABLE;
            long tx = StatsValues.UNAVAILABLE;
            try {
                rx = TrafficStats.getUidRxBytes(Process.myUid());
                tx = TrafficStats.getUidTxBytes(Process.myUid());
            } catch (RuntimeException ignored) {
                // Restricted or unsupported OS counters remain unavailable.
            }
            long now = SystemClock.elapsedRealtime();
            StatsValues.NetworkRates rates = network.sample(rx, tx, now);
            if (lastStorageRefresh < 0 || now - lastStorageRefresh >= STORAGE_REFRESH_MS) {
                storage = storageBytes();
                lastStorageRefresh = SystemClock.elapsedRealtime();
            }
            synchronized (StatsMonitor.this) {
                if (session != this) {
                    return;
                }
                final long sampledStorage = storage;
                handler.post(() -> {
                    synchronized (StatsMonitor.this) {
                        if (session != this) {
                            return;
                        }
                        output.setText(context.getString(R.string.stats_memory, formatBytes(memory))
                                + "\n" + context.getString(R.string.stats_network,
                                formatRate(rates.download), formatRate(rates.upload))
                                + "\n" + context.getString(R.string.stats_storage,
                                formatBytes(sampledStorage)));
                    }
                });
            }
        }
    }
}
