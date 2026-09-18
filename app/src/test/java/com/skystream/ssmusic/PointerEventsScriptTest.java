package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural regression checks; actual WebView hit testing still needs device verification. */
public class PointerEventsScriptTest {
    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/" + path)), StandardCharsets.UTF_8);
    }

    private String script() throws Exception {
        return read("main/assets/pointer_events.js");
    }

    @Test
    public void injectsCachedAssetUnconditionallyWithinPlaybackScope() throws Exception {
        String activity = read("main/java/com/skystream/ssmusic/MainActivity.java");
        String injection = activity.substring(activity.indexOf("private void injectPageScripts("),
                activity.indexOf("private void scheduleAppLogoReinjection("));
        assertTrue(activity.contains("getAssets().open(\"pointer_events.js\")"));
        assertTrue(activity.contains("if (pointerEventsScript == null)"));
        assertTrue(injection.contains("if (!SiteScope.isPlaybackUrl(view.getUrl()))"));
        assertTrue(injection.contains("view.evaluateJavascript(AD_HIDING_SCRIPT, null);\n"
                + "        view.evaluateJavascript(pointerEventsScript(), null);"));
        assertFalse(injection.contains("Logger.isEnabled()"));
        assertFalse(injection.contains("isDesktopMode"));
        assertFalse(script().contains("logDiagnostic"));
    }

    @Test
    public void installsOnceOnTopLevelMusicOriginAndOnlyTargetsStructuralContainers() throws Exception {
        String script = script();
        assertTrue(script.contains("location.origin !== 'https://music.youtube.com'"));
        assertTrue(script.contains("window !== window.top"));
        assertTrue(script.contains("|| window.__ssmusicPointerEventsInstalled"));
        assertTrue(script.indexOf("window.__ssmusicPointerEventsInstalled = true")
                < script.indexOf("new MutationObserver"));
        String scope = script.substring(script.indexOf("var layout ="), script.indexOf("var restricted ="));
        assertTrue(scope.contains("html > body > ytmusic-app > ytmusic-app-layout"));
        assertTrue(scope.contains("'html,html > body,html > body > ytmusic-app,'"));
        assertTrue(scope.contains("' > #layout > #content'"));
        assertTrue(scope.contains("' > #nav-bar > ytmusic-nav-bar'"));
        for (String forbidden : new String[]{"*", "button", "logo", "thumbnail", "img", "player-page"}) {
            assertFalse(forbidden, scope.contains(forbidden));
        }
        assertTrue(script.contains("document.querySelectorAll(containers).forEach"));
    }

    @Test
    public void repairsOnlyVisibleNoneValuesInAncestorOrderWithoutCrossingDisabledWrappers() throws Exception {
        String script = script();
        assertTrue(script.contains("!node.isConnected || node.closest(restricted) || !visible(node)"));
        assertTrue(script.contains("getComputedStyle(ancestor).pointerEvents === 'none'"));
        assertTrue(script.contains("return getComputedStyle(node).pointerEvents === 'none'"));
        assertTrue(script.contains("style.display === 'none' || style.visibility !== 'visible'"));
        assertTrue(script.contains("style.opacity === '0'"));
        assertTrue(script.contains("return rect.width > 0 && rect.height > 0"));
        assertTrue(script.contains("node.style.setProperty('pointer-events', 'auto', 'important')"));
        assertTrue(script.indexOf("restore();") < script.indexOf("document.querySelectorAll(containers)"));
    }

    @Test
    public void preservesRestrictionsDialogsFullscreenAndIntentionalOverlays() throws Exception {
        String script = script();
        for (String guard : new String[]{"[inert]", "[hidden]", "[disabled]", "[aria-hidden=\"true\"]",
                "[aria-disabled=\"true\"]", "window.__ssmusicKidModeInstalled",
                "document.fullscreenElement", "document.webkitFullscreenElement",
                "media.webkitDisplayingFullscreen", "PLAYER_PAGE_OPEN", "dialog[open]",
                "[role=\"dialog\"]", "[role=\"alertdialog\"]", "[aria-modal=\"true\"]",
                "tp-yt-paper-dialog", "tp-yt-iron-overlay-backdrop"}) {
            assertTrue(guard, script.contains(guard));
        }
        assertTrue(script.contains("document.querySelectorAll(dialogs)).some(function (node)"));
        assertTrue(script.contains("return visible(node, true)"));
        assertTrue(script.contains("!includeTransparent && style.opacity === '0'"));
        assertTrue(script.contains("if (!suspended && !blocked())"));
        for (String forbidden : new String[]{"setAttribute(", "removeAttribute(", ".disabled =",
                ".hidden =", ".inert =", ".click(", "dispatchEvent(", "preventDefault(",
                "stopPropagation(", "stopImmediatePropagation(", "touchstart", "pointerdown"}) {
            assertFalse(forbidden, script.contains(forbidden));
        }
    }

    @Test
    public void restoresOnlyOwnedInlineOverridesAndPreservesOriginalPriority() throws Exception {
        String script = script();
        assertTrue(script.contains("value: node.style.getPropertyValue('pointer-events')"));
        assertTrue(script.contains("priority: node.style.getPropertyPriority('pointer-events')"));
        assertTrue(script.contains("node.style.getPropertyValue('pointer-events') === 'auto'"));
        assertTrue(script.contains("node.style.getPropertyPriority('pointer-events') === 'important'"));
        assertTrue(script.contains("node.style.setProperty('pointer-events', previous.value, previous.priority)"));
        assertTrue(script.contains("node.style.removeProperty('pointer-events')"));
        assertTrue(script.contains("owned.clear()"));
    }

    @Test
    public void rechecksSpaAndGuardChangesWithoutPollingOrObservingOwnWrites() throws Exception {
        String script = script();
        String apply = script.substring(script.indexOf("function apply()"), script.indexOf("function schedule()"));
        assertTrue(apply.indexOf("observer.disconnect()") < apply.indexOf("restore()"));
        assertTrue(apply.contains("} finally {\n            if (!suspended) {\n                observe();"));
        assertTrue(script.contains("childList: true, subtree: true, attributes: true"));
        for (String attribute : new String[]{"style", "class", "id", "hidden", "inert", "disabled",
                "aria-hidden", "aria-disabled", "aria-modal", "role", "open", "opened", "player-ui-state"}) {
            assertTrue(attribute, script.contains("'" + attribute + "'"));
        }
        for (String event : new String[]{"yt-navigate-finish", "yt-page-data-updated", "fullscreenchange",
                "webkitfullscreenchange", "resize", "load", "pagehide", "pageshow"}) {
            assertTrue(event, script.contains("'" + event + "'"));
        }
        assertTrue(script.contains("if (!suspended && timer === null)"));
        assertTrue(script.contains("clearTimeout(timer)"));
        assertTrue(script.contains("observer.disconnect();\n        restore();"));
        assertTrue(script.contains("suspended = false;\n        apply();"));
        assertFalse(script.contains("setInterval("));
        assertFalse(script.contains("requestAnimationFrame("));
    }
}
