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
public class KidModeScriptTest {
    private String script() throws IOException {
        Path asset = Paths.get("src/main/assets/kid_mode.js");
        if (!Files.exists(asset)) {
            asset = Paths.get("app/src/main/assets/kid_mode.js");
        }
        return new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
    }

    @Test
    public void installsOnceAndReappliesOnSpaChangesWithoutCredentials() throws IOException {
        String script = script();
        assertTrue(script.contains("window.__ssmusicKidModeInstalled"));
        assertTrue(script.contains("new MutationObserver(function (changes)"));
        assertTrue(script.contains("'pushState', 'replaceState'"));
        assertTrue(script.contains("'popstate', 'hashchange'"));
        assertTrue(script.contains("'yt-navigate-start', 'yt-navigate-finish'"));
        assertFalse(script.contains("setInterval("));
        assertFalse(script.contains("addJavascriptInterface"));
        assertFalse(script.contains("getKidPassword"));
        assertFalse(script.contains("localStorage"));
    }

    @Test
    public void restrictsRoutesAndRejectsRadioAndUnverifiedInboundWatchLinks() throws IOException {
        String script = script();
        assertTrue(script.contains("https://music.youtube.com/library"));
        assertTrue(script.contains("!/^RD/i.test(value)"));
        assertTrue(script.contains("target.origin !== 'https://music.youtube.com'"));
        assertTrue(script.contains("target.pathname === '/playlist'"));
        assertTrue(script.contains("target.pathname === '/watch' && !!selected && selected.list === list"));
        assertTrue(script.contains("member(target.searchParams.get('v'))"));
        assertTrue(script.contains("target.searchParams.getAll('list').length !== 1"));
        assertTrue(script.contains("location.replace(LIBRARY)"));
    }

    @Test
    public void learnsMembershipOnlyFromBoundPlaylistDetailRows() throws IOException {
        String script = script();
        assertTrue(script.contains(
                "ytmusic-playlist-shelf-renderer ytmusic-responsive-list-item-renderer"));
        assertTrue(script.contains("page.pathname !== '/playlist'"));
        assertTrue(script.contains("(endpoint.playlistId || shelfList) === list"));
        assertTrue(script.contains("var item = data(row).playlistItemData"));
        assertTrue(script.contains("shelfList === list && item && videoId(item.videoId)"));
        assertTrue(script.contains("row.closest('ytmusic-player-queue,#automix,[hidden],[aria-hidden=\"true\"]')"));
        assertTrue(script.contains("selected && selected.list !== list"));
        assertTrue(script.contains("sessionStorage.setItem(STORAGE_KEY"));
        assertTrue(script.contains("saved.ids.every(videoId)"));
    }

    @Test
    public void guardsNativePlayAndNextAndChecksActualTrackChanges() throws IOException {
        String script = script();
        assertTrue(script.contains("HTMLMediaElement.prototype.play = function ()"));
        assertTrue(script.contains("Promise.reject(new DOMException("));
        assertTrue(script.contains("return nativePlay.apply(this, arguments)"));
        assertTrue(script.contains("'play', 'playing', 'timeupdate', 'loadstart', 'loadedmetadata'"));
        assertTrue(script.contains("api.getVideoData()"));
        assertTrue(script.contains("api.getPlaylistId()"));
        assertTrue(script.contains("member(track.video_id) && list === selected.list"));
        assertTrue(script.contains("target.closest('.next-button')"));
        assertTrue(script.contains("member(queue[index + direction])"));
        assertTrue(script.contains("event.stopImmediatePropagation()"));
        assertFalse(script.contains("track.video_id === page.searchParams.get('v')"));
    }

    @Test
    public void hidesDiscoveryButPreservesLogoAndTurnsAutoplayOff() throws IOException {
        String script = script();
        assertTrue(script.contains("ytmusic-settings-button,#mini-guide,ytmusic-search-box,ytmusic-menu-renderer,#automix"));
        assertTrue(script.contains("ytmusic-nav-bar button"));
        assertTrue(script.contains("!node.closest(LOGO) && !node.querySelector(LOGO)"));
        assertTrue(script.contains("ytmusic-logo a,a.logo,a.ytmusic-logo,a[href=\"/\"]"));
        assertTrue(script.contains("#tabsContainer [role=\"tab\"]"));
        assertTrue(script.contains("#tabsContainer .tab-header"));
        assertTrue(script.contains("value.title === 'Related'"));
        assertTrue(script.contains("toggle.click()"));
        assertTrue(script.contains("toggle.checked = false"));
        assertTrue(script.contains("toggle.disabled = true"));
        assertTrue(script.contains("media.autoplay = false"));
    }

    @Test
    public void blocksLocalizedRelatedTabsAndProgrammaticHiddenControlClicks() throws IOException {
        String script = script();
        assertTrue(script.contains("pageType === 'MUSIC_PAGE_TYPE_TRACK_RELATED'"));
        assertTrue(script.contains("siblings.length === 3 && siblings[2] === tab"));
        assertTrue(script.contains("var nav = target.closest(NAV_BUTTONS)"));
        assertTrue(script.contains("navButton(nav) || tab && relatedTab(tab)"));
    }

    @Test
    public void reassertsRestrictionsWithoutMutationLoopsAndCanResetSelection() throws IOException {
        String script = script();
        assertTrue(script.contains("'selected', 'style', 'disabled', 'src', 'autoplay'"));
        assertTrue(script.contains("node.style.getPropertyPriority('display') !== 'important'"));
        assertTrue(script.contains("if (!toggle.disabled)"));
        assertTrue(script.contains("change.attributeName === 'src'"));
        assertTrue(script.contains("window.__ssmusicResetKidPlaylist = function ()"));
        assertTrue(script.contains("sessionStorage.removeItem(STORAGE_KEY)"));
    }

    @Test
    public void restoresMembershipOnlyForAnAlreadyVerifiedWatchReload() throws IOException {
        String script = script();
        assertTrue(script.contains("var entry = url(location.href)"));
        assertTrue(script.contains("entry.pathname !== '/watch' || !allowed(entry)"));
        assertTrue(script.contains("var STORAGE_KEY = 'ssmusic.kid.playlist.v1'"));
    }
}
