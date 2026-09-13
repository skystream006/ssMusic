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
        assertTrue(script.contains("node.style.setProperty('opacity',hiddenOpacity,hiddenPriority)"));
        assertTrue(script.contains("else{node.style.removeProperty('opacity');}"));
        assertFalse(script.contains("setInterval("));
    }

    @Test
    public void videoDisplayScriptCanDefaultToPlayingVideo() {
        String script = MainActivity.videoDisplayScript(
                false, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("var DEFAULT=false"));
    }

    @Test
    public void videoDisplayScriptMapsSwipesToExistingPlayerControls() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("return dx<0?'left':'right'"));
        assertTrue(script.contains("return 'down'"));
        assertTrue(script.contains("action==='down'?'ytmusic-player-page .player-minimize-button"));
        assertTrue(script.contains("action==='left'?'ytmusic-player-bar .previous-button"));
        assertTrue(script.contains(":'ytmusic-player-bar .next-button"));
        assertTrue(script.contains("var control=swipeControl(action);if(control){control.click();}"));
        assertTrue(script.contains("window.__ssmusicVideoDisplayInstalled=true;installSwipes();"));
    }

    @Test
    public void videoDisplaySwipesLogCompletedActionsThroughDiagnosticBridge() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("function logSwipe(action,dispatched){try{"
                + "if(window.ssmusicPlayback&&window.ssmusicPlayback.logDiagnostic){"));
        assertTrue(script.contains("var command=action==='down'?'minimize'"
                + ":action==='up'?'up next':action==='left'?'previous':'next';"));
        assertTrue(script.contains("window.ssmusicPlayback.logDiagnostic("
                + "'Media player swipe '+action+': '+command"
                + "+(dispatched?' control clicked':' control unavailable'));"));
        assertTrue(script.contains("}catch(e){}}function installSwipes()"));
        assertTrue(script.contains("if(action!==gesture.action){return;}"
                + "var control=swipeControl(action);if(control){control.click();}"
                + "logSwipe(action,!!control);"));
    }

    @Test
    public void videoDisplaySwipeUpOpensOnlyUpNextAndLogsItsAction() {
        for (boolean showThumbnail : new boolean[]{true, false}) {
            String script = MainActivity.videoDisplayScript(
                    showThumbnail, "Show thumbnail", "Play video", "Song thumbnail");
            assertTrue(script.contains("if(-dy>=threshold&&-dy>Math.abs(dx)*1.25){return 'up';}"));
            assertTrue(script.contains(
                    ":action==='up'?'ytmusic-player-page .tab-header.ytmusic-player-page'"));
            assertTrue(script.contains(
                    "for(var i=0;i<controls.length&&(action!=='up'||i===0);i++){"));
            assertTrue(script.contains("if(action&&swipeControl(action)){swipe.action=action;}"));
            assertTrue(script.contains(
                    "var control=swipeControl(action);if(control){control.click();}logSwipe(action,!!control);"));
            assertTrue(script.contains(":action==='up'?'up next':"));
        }
    }

    @Test
    public void videoDisplaySwipesPreserveControlsAndRejectUnintendedGestures() {
        String script = MainActivity.videoDisplayScript(
                false, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("node.closest('ytmusic-player-page')"));
        assertTrue(script.contains("state==='PLAYER_PAGE_OPEN'||state==='FULLSCREEN'"));
        assertTrue(script.contains("!page.contains(target)"));
        assertTrue(script.contains("touch.clientX<rect.left||touch.clientX>rect.right"));
        assertTrue(script.contains("touch.clientY<rect.top||touch.clientY>rect.bottom"));
        assertTrue(script.contains("target.closest('button,a,input,select,textarea"));
        assertTrue(script.contains("event.touches.length!==1"));
        assertTrue(script.contains("touch.identifier!==gesture.id"));
        assertTrue(script.contains("touch.clientX-gesture.x,touch.clientY-gesture.y,60"));
        assertTrue(script.contains("Math.abs(dx)>Math.abs(dy)*1.25"));
        assertTrue(script.contains("if(action!==gesture.action){return;}"));
        assertTrue(script.contains("control.getAttribute('aria-disabled')!=='true'"));
        assertTrue(script.contains("event.preventDefault();event.stopImmediatePropagation();"));
        assertTrue(script.contains("'touchcancel',function(){swipe=null;}"));
    }

    @Test
    public void jsStringLiteralEscapesUnsafeCharacters() {
        assertEquals("'Play \\'video\\' \\\\ now\\n'",
                MainActivity.jsStringLiteral("Play 'video' \\ now\n"));
    }
}
