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
    public void videoDisplayScriptMapsSwipesToCompactAndExistingTransportControls() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("if(action==='down'){var compact=window.__ssmusicCompactPlayer;"));
        assertTrue(script.contains("action==='left'?'ytmusic-player-bar .previous-button"));
        assertTrue(script.contains(":'ytmusic-player-bar .next-button"));
        assertTrue(script.contains("var control=swipeControl(action);if(control){control.click();}"));
        assertTrue(script.contains("window.__ssmusicVideoDisplayInstalled=true;installSwipes();"));
    }

    @Test
    public void videoDisplaySwipeDownUsesAppPresentationOnlyAfterGenerationAndMediaGuards() {
        for (boolean showThumbnail : new boolean[]{true, false}) {
            String script = MainActivity.videoDisplayScript(
                    showThumbnail, "Show thumbnail", "Play video", "Song thumbnail");
            assertFalse(script.contains(".player-minimize-button"));
            assertFalse(script.contains(".toggle-player-page-button"));
            int compactDispatch = script.indexOf("if(action==='down'){");
            int expandedPlayerGuard = script.indexOf("||!mediaPage(gesture.node)");
            assertTrue(expandedPlayerGuard >= 0);
            assertTrue(expandedPlayerGuard < compactDispatch);
            assertTrue(script.indexOf("gesture.id!==id") < compactDispatch);
            assertTrue(script.indexOf("return 'rejected: unknown direction'") < compactDispatch);
            assertTrue(compactDispatch < script.indexOf("var control=swipeControl(action)"));
            assertTrue(script.contains("return 'Media player swipe down: compact '"
                    + "+(compact&&compact.compact()?'applied':'unavailable');}"));
            assertTrue(script.contains("!control.disabled&&!control.hasAttribute('disabled')"
                    + "&&control.getAttribute('aria-disabled')!=='true'"));
            assertTrue(script.contains("rect.width>0&&rect.height>0"
                    + "&&getComputedStyle(control).visibility!=='hidden'"));
        }
    }

    @Test
    public void videoDisplaySwipesReturnActionDiagnosticsToNativeLogger() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("var command=action==='up'?'up next':action==='left'?'previous':'next';"));
        assertTrue(script.contains("return 'Media player swipe '+action+': '+command"
                + "+(control?' control clicked':' control unavailable');"));
        assertTrue(script.contains("return 'rejected: no hit target'"));
        assertTrue(script.contains("return 'rejected: no expanded media surface'"));
        assertTrue(script.contains("return 'rejected: nested control'"));
        assertTrue(script.contains("return 'rejected: outside media bounds'"));
    }

    @Test
    public void videoDisplaySwipeUpOpensOnlyUpNextAndLogsItsAction() {
        for (boolean showThumbnail : new boolean[]{true, false}) {
            String script = MainActivity.videoDisplayScript(
                    showThumbnail, "Show thumbnail", "Play video", "Song thumbnail");
            assertTrue(script.contains(
                    "var selector=action==='up'?'ytmusic-player-page .tab-header.ytmusic-player-page'"));
            assertTrue(script.contains(
                    "for(var i=0;i<controls.length&&(action!=='up'||i===0);i++){"));
            assertTrue(script.contains("var control=swipeControl(action);if(control){control.click();}"));
            assertTrue(script.contains("var command=action==='up'?'up next':"));
        }
    }

    @Test
    public void videoDisplaySwipesPreserveControlsAndRejectUnintendedGestures() {
        String script = MainActivity.videoDisplayScript(
                false, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("node.closest('ytmusic-player-page')"));
        assertTrue(script.contains("state==='PLAYER_PAGE_OPEN'||state==='FULLSCREEN'"));
        assertTrue(script.contains("!page.contains(target)"));
        assertFalse(script.contains("!state||"));
        assertTrue(script.contains("x<rect.left||x>rect.right"));
        assertTrue(script.contains("y<rect.top||y>rect.bottom"));
        assertTrue(script.contains("target.closest('button,a,input,select,textarea"));
        assertTrue(script.contains("[role=\"slider\"]"));
        assertTrue(script.contains("[role=\"tab\"]"));
        assertTrue(script.contains("gesture.id!==id"));
        assertTrue(script.contains("gesture.url!==location.href||!mediaPage(gesture.node)"));
        assertTrue(script.contains("['down','up','left','right'].indexOf(action)<0"));
        assertTrue(script.contains("control.getAttribute('aria-disabled')!=='true'"));
        assertTrue(script.contains("document.addEventListener('yt-navigate-start',function(){navigating=true;clearSwipe();},true)"));
        assertTrue(script.contains("document.addEventListener('yt-navigate-finish',function(){navigating=false;clearSwipe();},true)"));
        assertTrue(script.contains("if(navigating){return 'rejected: navigation in progress';}"));
        assertTrue(script.contains("window.addEventListener('popstate',clearSwipe,true)"));
        assertTrue(script.contains("window.addEventListener('pagehide',clearSwipe,true)"));
        assertTrue(script.contains("location.origin!=='https://music.youtube.com'"));
    }

    @Test
    public void videoDisplaySwipesUseMediaSurfaceInsteadOfHiddenVideoBounds() {
        for (boolean showThumbnail : new boolean[]{true, false}) {
            String script = MainActivity.videoDisplayScript(
                    showThumbnail, "Show thumbnail", "Play video", "Song thumbnail");
            assertTrue(script.contains("ytmusic-player-page #movie_player"));
            assertTrue(script.contains("ytmusic-player-page #song-image"));
            assertTrue(script.contains("ytmusic-player-page #song-media-window"));
            assertTrue(script.contains("ytmusic-player-page .song-media-window"));
            assertTrue(script.contains("ytmusic-player-page ytmusic-player"));
            assertTrue(script.contains("var node=target.closest(MEDIA_SELECTOR);var page=mediaPage(node);"));
            assertFalse(script.contains("var node=video();var page=mediaPage(node);"));
            assertTrue(script.contains("control&&control!==node;control=control.parentElement"));
            assertTrue(script.contains("if(control.getAttribute('role')==='button'){return 'rejected: nested button';}"));
            assertFalse(script.contains("textarea,[role=\"button\"]"));
        }
    }

    @Test
    public void videoDisplaySwipesUseNativeReceptionWithoutDuplicateDomListeners() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("window.__ssmusicNativeSwipeStart=function(id,fx,fy)"));
        assertTrue(script.contains("window.__ssmusicNativeSwipeEnd=function(id,action)"));
        assertFalse(script.contains("addEventListener('touch"));
        assertFalse(script.contains("touch-action:none"));
        assertFalse(script.contains("preventDefault()"));
    }

    @Test
    public void nativeHitTestConvertsViewFractionsToVisibleCssViewport() {
        String script = MainActivity.videoDisplayScript(
                true, "Show thumbnail", "Play video", "Song thumbnail");
        assertTrue(script.contains("var viewport=window.visualViewport"));
        assertTrue(script.contains("(viewport?viewport.offsetLeft:0)+fx*(viewport?viewport.width:window.innerWidth)"));
        assertTrue(script.contains("(viewport?viewport.offsetTop:0)+fy*(viewport?viewport.height:window.innerHeight)"));
        assertTrue(script.contains("document.elementFromPoint(x,y)"));
        assertFalse(script.contains("devicePixelRatio"));
    }

    @Test
    public void jsStringLiteralEscapesUnsafeCharacters() {
        assertEquals("'Play \\'video\\' \\\\ now\\n'",
                MainActivity.jsStringLiteral("Play 'video' \\ now\n"));
    }
}
