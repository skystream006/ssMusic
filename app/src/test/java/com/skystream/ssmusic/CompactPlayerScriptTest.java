package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural regression checks; live YouTube Music behavior requires device verification. */
public class CompactPlayerScriptTest {
    private String read(String relative) throws IOException {
        Path path = Paths.get("src/" + relative);
        if (!Files.exists(path)) {
            path = Paths.get("app/src/" + relative);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String script() throws IOException {
        return read("main/assets/compact_player.js");
    }

    @Test
    public void injectsLocalizedAssetBeforeSwipeHandlersWithoutChangingKidMode() throws IOException {
        String activity = read("main/java/com/skystream/ssmusic/MainActivity.java");
        assertTrue(activity.contains("getAssets().open(\"compact_player.js\")"));
        assertTrue(activity.contains("jsStringLiteral(getString(R.string.compact_player_button))"));
        assertTrue(activity.contains("jsStringLiteral(getString(R.string.expand_player_button))"));
        int injection = activity.indexOf("view.evaluateJavascript(compactPlayerScript(), null)");
        assertTrue(injection > activity.indexOf("view.evaluateJavascript(kidModeScript(), null)"));
        assertTrue(injection < activity.indexOf("view.evaluateJavascript(videoDisplayScript("));
    }

    @Test
    public void keepsOriginalMediaAndSitePlayerStateUntouched() throws IOException {
        String script = script();
        assertTrue(script.contains("transform:scale("));
        assertTrue(script.contains("candidate[key] === active[key]"));
        assertTrue(script.contains("'pageState', 'layoutState'"));
        assertTrue(script.contains("!media.isConnected"));
        for (String forbidden : new String[]{"playerResponse", "isKids", "HTMLMediaElement.prototype",
                ".play(", ".pause(", ".load(", "cloneNode", "innerHTML", "src =", "src=",
                "setAttribute('player-ui-state'", "history.", "location.assign", "location.replace",
                "createElement('video')", "createElement('audio')"}) {
            assertFalse(forbidden, script.contains(forbidden));
        }
    }

    @Test
    public void exposesBrowseAndRestoresOnlyOwnedPresentation() throws IOException {
        String script = script();
        assertTrue(script.contains("mark(candidate.browse, 'browse')"));
        assertTrue(script.contains("mark(candidate.page, 'page')"));
        assertTrue(script.contains("browse.contains(page)"));
        assertTrue(script.contains("display:block!important;visibility:visible!important"));
        assertTrue(script.contains("window.innerHeight - barRect.top + 12"));
        assertTrue(script.contains("compactStyle.remove()"));
        assertTrue(script.contains("previous: node.getAttribute(ROLE)"));
        assertTrue(script.contains("entry.node.setAttribute(ROLE, entry.previous)"));
        assertFalse(script.contains("page.style."));
        assertFalse(script.contains("media.style."));
    }

    @Test
    public void interceptsOnlyDedicatedPresentationControlsAndProvidesAccessibleButton() throws IOException {
        String script = script();
        assertTrue(script.contains("ytmusic-player-page .player-minimize-button"));
        assertTrue(script.contains("ytmusic-player-bar .toggle-player-page-button"));
        assertTrue(script.contains("control.getAttribute('aria-disabled') === 'true'"));
        assertTrue(script.contains("event.stopImmediatePropagation()"));
        assertTrue(script.contains("button.type = 'button'"));
        assertTrue(script.contains("button.setAttribute('aria-expanded', expanded)"));
        assertTrue(script.contains("button.setAttribute('aria-label', label)"));
        assertFalse(script.contains(".next-button"));
        assertFalse(script.contains(".previous-button"));
        assertFalse(script.contains(".play-pause-button"));
        assertFalse(script.contains("control.click()"));
    }

    @Test
    public void guardsUnsupportedStatesAndCleansUpWithoutMutationLoops() throws IOException {
        String script = script();
        assertTrue(script.contains("location.origin !== 'https://music.youtube.com'"));
        assertTrue(script.contains("window.__ssmusicCompactPlayer)"));
        assertTrue(script.contains("!== 'PLAYER_PAGE_OPEN'"));
        assertTrue(script.contains("document.fullscreenElement || document.webkitFullscreenElement"));
        assertTrue(script.contains("if (active && !same(current()))"));
        assertTrue(script.contains("window.addEventListener('pagehide'"));
        assertTrue(script.contains("observer.disconnect()"));
        assertTrue(script.contains("window.addEventListener('pageshow'"));
        assertTrue(script.contains("if (compactStyle.textContent !== css)"));
        assertTrue(script.contains("if (button.style.bottom !== bottom)"));
        assertTrue(script.contains("if (!suspended && timer === null)"));
        assertFalse(script.contains("setInterval("));
    }

    @Test
    public void refreshesThumbnailOnlyAfterPresentationGeometryChanges() throws IOException {
        String script = script();
        assertTrue(script.contains("!suspended && typeof window.__ssmusicApplyVideoDisplay === 'function'"));
        assertTrue(script.contains("window.__ssmusicApplyVideoDisplay()"));
        assertTrue(script.contains("active = null;\n        refreshVideoDisplay();"));
        assertTrue(script.contains(
                "var geometryChanged = compactStyle.textContent !== css || !compactStyle.isConnected"));
        assertTrue(script.contains("if (geometryChanged) {\n            refreshVideoDisplay();"));
        assertTrue(script.indexOf("document.head.appendChild(compactStyle)")
                < script.indexOf("if (geometryChanged)"));
    }

    @Test
    public void transientGeometryDoesNotDiscardCompactPresentation() throws IOException {
        String script = script();
        String current = script.substring(script.indexOf("function current()"),
                script.indexOf("function same("));
        assertFalse(current.contains("rect.width <= 0"));
        assertFalse(current.contains("getComputedStyle"));
        assertTrue(script.contains("if (active && !same(candidate)) {\n            expand();"));
        assertFalse(script.contains("!same(candidate) || !present(candidate)"));
        assertTrue(script.contains("if (active && !present(candidate)) {\n"
                + "            button.hidden = true;\n            return;"));
        assertTrue(script.contains("if (!geometry(candidate))"));
        assertTrue(script.contains("!Number.isFinite(scale)"));
        assertTrue(script.contains("barRect.bottom > window.innerHeight + 1"));
    }

    @Test
    public void cachedPageRetainsPresentationButUnloadedPageCleansUp() throws IOException {
        String script = script();
        String hide = script.substring(script.indexOf("window.addEventListener('pagehide'"),
                script.indexOf("window.addEventListener('pageshow'"));
        assertTrue(hide.contains("suspended = true"));
        assertTrue(hide.contains("observer.disconnect()"));
        assertTrue(hide.contains("if (!event.persisted) {\n            expand();"));
        assertTrue(script.contains("suspended = false;\n        observe();\n        schedule();"));
    }

    @Test
    public void foregroundRefreshesExistingControllersWithoutReinjectingOrReloading() throws IOException {
        String activity = read("main/java/com/skystream/ssmusic/MainActivity.java");
        String resume = activity.substring(activity.indexOf("protected void onResume()"),
                activity.indexOf("protected void onPause()"));
        assertTrue(resume.contains("webView.post("));
        assertTrue(resume.contains("SiteScope.isPlaybackUrl(webView.getUrl())"));
        assertTrue(resume.contains("window.__ssmusicCompactPlayer.refresh()"));
        assertTrue(resume.contains("window.__ssmusicApplyVideoDisplay()"));
        assertTrue(script().contains("refresh: schedule"));
        assertFalse(resume.contains("injectPageScripts("));
        assertFalse(resume.contains("reload("));
        assertFalse(resume.contains("loadUrl("));
    }
}
