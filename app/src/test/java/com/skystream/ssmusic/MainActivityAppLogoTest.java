package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainActivityAppLogoTest {

    @Test
    public void matchesAppLogoRequestsOnAnyOrigin() {
        assertTrue(MainActivity.isAppLogoRequest(
                "https://music.youtube.com" + MainActivity.APP_LOGO_PATH));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://m.youtube.com" + MainActivity.APP_LOGO_PATH + "?v=1"));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://music.youtube.com" + MainActivity.APP_LOGO_PATH + "#frag"));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://music.youtube.com/SSMusic_App_Logo.png"));
    }

    @Test
    public void ignoresOtherRequests() {
        assertFalse(MainActivity.isAppLogoRequest(null));
        assertFalse(MainActivity.isAppLogoRequest("https://music.youtube.com/watch?v=abc"));
        assertFalse(MainActivity.isAppLogoRequest(
                "https://music.youtube.com/other" + MainActivity.APP_LOGO_PATH));
        assertFalse(MainActivity.isAppLogoRequest("about:blank"));
        assertFalse(MainActivity.isAppLogoRequest(MainActivity.APP_LOGO_PATH));
    }

    @Test
    public void logoScriptTargetsMusicWordmarkAndServesBundledLogo() {
        String script = MainActivity.APP_LOGO_SCRIPT;
        assertTrue(script.startsWith("(function("));
        assertTrue(script.contains("ytmusic-logo"));
        assertTrue(script.contains(MainActivity.APP_LOGO_PATH));
        assertTrue(script.contains("location.origin"));
    }

    @Test
    public void videoDisplayScriptAddsToggleAndPersistsDefault() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.startsWith("(function("));
        assertTrue(script.contains("var DEFAULT=true"));
        assertTrue(script.contains("ssmusic-video-display-root"));
        assertTrue(script.contains("ssmusic-video-thumbnail-cover"));
        assertTrue(script.contains("SHOW_THUMBNAIL_LABEL='Show thumbnail'"));
        assertTrue(script.contains("SHOW_VIDEO_LABEL='Play video'"));
        assertTrue(script.contains("THUMBNAIL_ALT='Song thumbnail'"));
        assertTrue(script.contains("setVideoThumbnailDefault"));
        assertTrue(script.contains("__ssmusicSetVideoThumbnailDefault"));
        assertTrue(script.contains("observer=new MutationObserver(function(){scheduleApply();})"));
        assertTrue(script.contains("transform:translateX(-50%)"));
        assertTrue(script.contains("cursor:pointer"));
        assertTrue(script.contains("node.style.setProperty('opacity','0','important')"));
        assertTrue(script.contains("hidden.style.removeProperty('opacity')"));
        assertFalse(script.contains("setInterval("));
    }

    @Test
    public void videoDisplayScriptCanDefaultToPlayingVideo() {
        String script = MainActivity.videoDisplayScript(
                false, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("var DEFAULT=false"));
    }

    @Test
    public void jsStringLiteralEscapesUnsafeCharacters() {
        assertEquals("'Play \\'video\\' \\\\ now\\n'",
                MainActivity.jsStringLiteral("Play 'video' \\ now\n"));
    }
}
