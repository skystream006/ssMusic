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
    public void wiresNativeInputAndRefreshWithoutTreatingServiceLifecycleAsInput() throws IOException {
        Path path = Paths.get("src/main/java/com/skystream/ssmusic/MainActivity.java");
        if (!Files.exists(path)) {
            path = Paths.get("app/src/main/java/com/skystream/ssmusic/MainActivity.java");
        }
        String activity = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
}
