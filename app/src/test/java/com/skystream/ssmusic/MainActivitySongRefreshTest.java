package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural regression checks; the Android unit-test runtime has no JavaScript engine. */
public class MainActivitySongRefreshTest {
    @Test
    public void replacesPromptConfirmationWithScopedSongTracking() {
        String script = MainActivity.SONG_REFRESH_SCRIPT;
        assertTrue(script.contains("location.origin!=='https://music.youtube.com'"));
        assertTrue(script.contains("window!==window.top"));
        assertTrue(script.contains("if(window.__ssmusicSongRefreshInstalled){return;}"));
        assertTrue(script.contains("bridge.songStarted(id)"));
        assertFalse(script.contains("dialog-confirm"));
        assertFalse(script.contains(".click()"));
        assertFalse(script.contains("textContent"));
        assertFalse(script.contains(".play("));
        assertFalse(MainActivity.AD_HIDING_SCRIPT.contains("ytmusic-you-there-renderer"));
    }

    @Test
    public void countsOnlyMainPlayerPlaybackWithAStableVideoIdentity() {
        String script = MainActivity.SONG_REFRESH_SCRIPT;
        assertTrue(script.contains("document.querySelector('#movie_player')"));
        assertTrue(script.contains("event&&event.target!==media"));
        assertTrue(script.contains("media.paused||media.ended||media.seeking||media.readyState<3"));
        assertTrue(script.contains("player.classList.contains('ad-showing')"));
        assertTrue(script.contains("data=player.getVideoData()"));
        assertTrue(script.contains("data.video_id"));
        assertTrue(script.contains("if(id!==lastId){lastId=id;bridge.songStarted(id);}"));
        assertFalse(script.contains("location.href"));
        assertFalse(script.contains("currentSrc"));
    }

    @Test
    public void detectsLateStartsAndRepeatsWithoutCountingPauseOrBuffering() {
        String script = MainActivity.SONG_REFRESH_SCRIPT;
        assertTrue(script.contains("document.addEventListener('playing',report,true)"));
        assertTrue(script.contains("document.addEventListener('timeupdate',report,true)"));
        assertTrue(script.contains("document.addEventListener('ended',function(event)"));
        assertTrue(script.contains("if(!player||player.classList.contains('ad-showing')){return;}"));
        assertTrue(script.contains("event.target===lastMedia&&lastId"));
        assertTrue(script.contains("window.ssmusicPlayback.songEnded(lastId);lastId=''"));
        assertTrue(script.endsWith("report();})()"));
        assertFalse(script.contains("addEventListener('pause'"));
        assertFalse(script.contains("addEventListener('waiting'"));
        assertFalse(script.contains("setInterval"));
    }

    @Test
    public void onlyRealPageInputResetsTheCount() {
        String script = MainActivity.SONG_REFRESH_SCRIPT;
        assertTrue(script.contains("['pointerdown','touchstart','keydown','wheel','click']"));
        assertTrue(script.contains("if(event.isTrusted&&window.ssmusicPlayback)"));
        assertTrue(script.contains("window.ssmusicPlayback.userInteracted()"));
        assertTrue(script.contains("{capture:true,passive:true}"));
    }

    @Test
    public void backgroundChecksReuseSongDetectionWithoutPageTimersOrVisibilityGates() throws IOException {
        String script = MainActivity.SONG_REFRESH_SCRIPT;
        assertTrue(script.contains("window.__ssmusicReportSongStart=report;"));
        assertFalse(script.contains("document.hidden"));
        assertFalse(script.contains("visibilityState"));
        assertFalse(script.contains("requestAnimationFrame"));
        assertFalse(script.contains("setInterval"));

        String activity = activitySource();
        String check = activity.substring(activity.indexOf("private final Handler songRefreshHandler"),
                activity.indexOf("private final BroadcastReceiver mediaCommandReceiver"));
        assertTrue(check.contains("new Handler(Looper.getMainLooper())"));
        assertTrue(check.contains("playbackBridgeEnabled && SiteScope.isPlaybackUrl(webView.getUrl())"));
        assertTrue(check.contains("window.__ssmusicReportSongStart();"));
        assertTrue(check.contains("songRefreshHandler.postDelayed(this, 5000L)"));
        assertFalse(check.contains("songRefreshCounter.reset()"));
        assertFalse(check.contains("playbackActive"));

        String pause = activity.substring(activity.indexOf("protected void onPause()"),
                activity.indexOf("protected void onSaveInstanceState("));
        assertTrue(pause.contains("if (!isFinishing()) {\n"
                + "            songRefreshHandler.post(backgroundSongCheck);"));
        assertTrue(pause.indexOf("removeCallbacks(backgroundSongCheck)")
                < pause.indexOf("post(backgroundSongCheck)"));
        String resume = activity.substring(activity.indexOf("protected void onResume()"),
                activity.indexOf("public void onUserInteraction()"));
        assertTrue(resume.contains("songRefreshHandler.removeCallbacks(backgroundSongCheck)"));
        assertFalse(resume.contains("songRefreshCounter.reset()"));
        String stop = activity.substring(activity.indexOf("protected void onStop()"),
                activity.indexOf("protected void onDestroy()"));
        assertFalse(stop.contains("songRefreshHandler.remove"));
        assertFalse(stop.contains("songRefreshCounter.reset()"));
        String destroy = activity.substring(activity.indexOf("protected void onDestroy()"),
                activity.indexOf("public void onTrimMemory("));
        assertTrue(destroy.contains("songRefreshHandler.removeCallbacks(backgroundSongCheck)"));
    }

    @Test
    public void wiresNativeInputAndRefreshWithoutTreatingServiceLifecycleAsInput() throws IOException {
        String activity = activitySource();
        assertTrue(activity.contains("view.evaluateJavascript(SONG_REFRESH_SCRIPT, null)"));
        assertFalse(activity.contains("STILL_LISTENING_SCRIPT"));
        assertTrue(activity.contains("super.onUserInteraction();\n        songRefreshCounter.reset();"));
        assertTrue(activity.contains("&& songRefreshCounter.songStarted(videoId))"));
        String started = activity.substring(activity.indexOf("public void songStarted("),
                activity.indexOf("public void songEnded("));
        assertTrue(started.contains("runOnUiThread"));
        assertTrue(started.contains("SiteScope.isPlaybackUrl(webView.getUrl())"));
        assertTrue(started.contains("webView.reload()"));
        String commands = activity.substring(activity.indexOf("private void applyMediaCommand("),
                activity.indexOf("private synchronized void updatePlaybackService("));
        assertTrue(commands.indexOf("return;") < commands.indexOf("songRefreshCounter.reset();"));
    }

    private static String activitySource() throws IOException {
        Path path = Paths.get("src/main/java/com/skystream/ssmusic/MainActivity.java");
        if (!Files.exists(path)) {
            path = Paths.get("app/src/main/java/com/skystream/ssmusic/MainActivity.java");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
