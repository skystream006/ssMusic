package com.skystream.ssmusic;

/** Tracks song starts on the UI thread, retaining identity across a page refresh. */
final class SongRefreshCounter {
    private int songCount;
    private String lastVideoId;

    boolean songStarted(String videoId) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")
                || videoId.equals(lastVideoId)) {
            return false;
        }
        lastVideoId = videoId;
        songCount++;
        if (songCount == 6) {
            reset();
            return true;
        }
        return false;
    }

    void songEnded(String videoId) {
        if (videoId != null && videoId.equals(lastVideoId)) {
            lastVideoId = null;
        }
    }

    void reset() {
        // Resuming or reloading the current song must not count it a second time.
        songCount = 0;
    }
}
