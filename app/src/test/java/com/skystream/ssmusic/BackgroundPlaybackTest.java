package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural regression checks for lifecycle code and the injected playback script. */
public class BackgroundPlaybackTest {
    @Test
    public void bufferingRemainsActiveEvenWithoutCurrentMediaData() {
        String script = MainActivity.BACKGROUND_PLAYBACK_SCRIPT;
        assertTrue(script.contains("if(!tentative&&!node.paused&&!node.ended){"));
        assertTrue(script.contains("if(!active&&tentative){active=tentative;playing=true;}"));
        assertFalse(script.contains("!node.ended&&node.readyState>=2"));
        assertTrue(script.contains("document.addEventListener('waiting',scheduleReport,true)"));
        assertTrue(script.contains("document.addEventListener('playing',scheduleReport,true)"));
    }

    @Test
    public void actualPauseAndEndStillReportAndDoNotForcePlayback() {
        String script = MainActivity.BACKGROUND_PLAYBACK_SCRIPT;
        assertTrue(script.contains("var playing=false;"));
        assertTrue(script.contains("if(!node.paused&&!node.ended&&node.readyState>2){"));
        assertTrue(script.contains("document.addEventListener('pause',scheduleReport,true)"));
        assertTrue(script.contains("document.addEventListener('ended',scheduleReport,true)"));
        assertTrue(script.contains("window.ssmusicPlayback.setPlaying(playing)"));
        assertFalse(script.contains(".play("));
    }

    @Test
    public void startsServiceBeforeActivityIsStopped() throws IOException {
        String activity = source("MainActivity");
        String pause = section(activity, "protected void onPause()", "protected void onSaveInstanceState");
        assertTrue(pause.contains("!isFinishing() && isPlaybackLikelyActive()"));
        assertTrue(pause.contains("startPlaybackKeepAliveService(playbackActive ? Boolean.TRUE : null)"));
        assertTrue(pause.indexOf("startPlaybackKeepAliveService(") < pause.indexOf("super.onPause()"));
        String stop = section(activity, "protected void onStop()", "protected void onDestroy()");
        assertFalse(stop.contains("startPlaybackKeepAliveService("));
    }

    @Test
    public void runningServiceUpdatesDoNotRequestForegroundStartupAgain() throws IOException {
        String activity = source("MainActivity");
        String start = section(activity, "private void startPlaybackKeepAliveService(",
                "private void stopPlaybackKeepAliveService()");
        assertTrue(start.contains("if (!keepAliveServiceRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)"));
        assertTrue(start.contains("startForegroundService(serviceIntent);"));
        assertTrue(start.contains("} else {\n                startService(serviceIntent);"));

        String service = source("PlaybackKeepAliveService");
        String refresh = section(service, "if (foreground) {", "// The notification stays up");
        assertTrue(refresh.contains("manager.notify(NOTIFICATION_ID, notification);"));
        assertTrue(refresh.contains("startForeground(NOTIFICATION_ID, notification"));
        assertTrue(refresh.contains("foreground = true;"));
        assertTrue(refresh.indexOf("} else {") < refresh.indexOf("startForeground("));
        assertTrue(refresh.indexOf("foreground = true;") > refresh.lastIndexOf("startForeground("));
        assertTrue(service.contains("if (!foreground || ACTION_STOP.equals(intent == null ? null : intent.getAction()))"));
    }

    @Test
    public void pauseKeepsNotificationButExplicitStopRemovesIt() throws IOException {
        String service = source("PlaybackKeepAliveService");
        String state = section(service, "private void setPlaying(", "private void handleMediaCommand(");
        assertFalse(state.contains("stopSelf("));
        assertFalse(state.contains("stopForeground("));
        String stop = section(service, "private void stopPlayback()", "public void onDestroy()");
        assertTrue(stop.contains("stopForeground(STOP_FOREGROUND_REMOVE)"));
        assertTrue(stop.contains("foreground = false;"));
        assertTrue(stop.contains("stopSelf();"));
        assertFalse(service.contains("requestAudioFocus("));

        String webView = source("PlaybackWebView");
        assertTrue(webView.contains("if (visibility == View.VISIBLE)"));
        assertTrue(webView.contains("super.onWindowVisibilityChanged(visibility)"));
    }

    @Test
    public void duplicatePlaybackReportsStillDoNotRestartService() throws IOException {
        String activity = source("MainActivity");
        String update = section(activity, "private synchronized void updatePlaybackService(",
                "private final class PlaybackBridge");
        assertTrue(update.contains("if (playbackActive == playing) {\n            return;"));
    }

    private static String source(String name) throws IOException {
        Path path = Paths.get("src/main/java/com/skystream/ssmusic/" + name + ".java");
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(path);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        assertTrue("Missing section: " + start, startIndex >= 0);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue("Missing section end: " + end, endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }
}
