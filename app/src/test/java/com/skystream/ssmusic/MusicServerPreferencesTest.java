package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class MusicServerPreferencesTest {
    @Test
    public void currentSongComesFromPlayerNotPotentiallyDifferentBrowsingPage() throws Exception {
        String source = source("MusicServerPreferences");
        assertTrue(source.contains("player.getVideoData()"));
        assertTrue(source.contains("media.ended"));
        assertTrue(source.contains("ad-showing"));
        assertTrue(source.contains("location.origin!=='https://music.youtube.com'"));
        assertTrue(source.contains("MusicServerUrls.songUrlForId"));
        assertFalse(source.contains("lastReportedPositionUrl"));
    }

    @Test
    public void callbackIsHandledOnColdAndWarmStartsWithoutNavigatingOrLoggingIt() throws Exception {
        String main = source("MainActivity");
        assertTrue(main.contains("musicServerPreferences.handleIntent(getIntent())"));
        String warm = main.substring(main.indexOf("protected void onNewIntent("),
                main.indexOf("public void onRequestPermissionsResult("));
        assertTrue(warm.indexOf("musicServerPreferences.handleIntent(intent)")
                < warm.indexOf("urlFromIntent(intent)"));
        String ui = source("MusicServerPreferences");
        assertTrue(ui.indexOf("intent.setData(null)") < ui.indexOf("client.completeLogin("));
        assertFalse(ui.contains("Logger."));
    }

    @Test
    public void usesExternalBrowserAndRefreshesPlaylistVisibility() throws Exception {
        String source = source("MusicServerPreferences");
        assertTrue(source.contains("new Intent(Intent.ACTION_VIEW, Uri.parse(url))"));
        assertTrue(source.contains("activity.startActivity(browser)"));
        assertTrue(source.contains("loggedIn && MusicServerUrls.playlistUrl(webView.getUrl()) != null"));
        assertTrue(source.contains("handler.postDelayed(this, 1000L)"));
        assertTrue(source.contains("client.cancelLogin()"));
        assertTrue(source.contains("client.isBusy() || resolvingSong"));
        assertFalse(source.contains("webView.loadUrl("));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssmusic/" + name + ".java")), StandardCharsets.UTF_8);
    }
}
