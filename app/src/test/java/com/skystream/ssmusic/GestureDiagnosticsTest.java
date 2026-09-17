package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural guards for observational diagnostics; device testing verifies Chromium dispatch. */
public class GestureDiagnosticsTest {
    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/" + path)), StandardCharsets.UTF_8);
    }

    private String script() throws Exception {
        return read("main/assets/gesture_diagnostics.js");
    }

    @Test
    public void pageDiagnosticsAreOptInOriginScopedAndInstalledOnce() throws Exception {
        String script = script();
        assertTrue(script.contains("location.origin !== 'https://music.youtube.com'"));
        assertTrue(script.contains("window !== window.top"));
        assertTrue(script.contains("window.__ssmusicGestureDiagnosticsInstalled"));
        assertTrue(script.contains("window.__ssmusicGestureLoggingEnabled === true"));
        assertTrue(script.contains("if (!enabled() || !event.isTrusted || pending >= 16)"));
        assertTrue(script.contains("if (!enabled())"));
        assertTrue(script.contains("if (enabled()) {\n                bridge.logDiagnostic"));
        String activity = read("main/java/com/skystream/ssmusic/MainActivity.java");
        assertTrue(activity.contains("view.evaluateJavascript(gestureDiagnosticsScript(), null)"));
        assertTrue(activity.contains("\"window.__ssmusicGestureLoggingEnabled=\" + Logger.isEnabled()"));
        assertTrue(activity.contains("webView != null && SiteScope.isPlaybackUrl(webView.getUrl())"));
    }

    @Test
    public void recordsDispatchAndHitTestingWithoutChangingEventsOrLeakingContent() throws Exception {
        String script = script();
        assertTrue(script.contains("'pointerdown', 'pointerup', 'pointercancel', 'touchstart', 'touchend', 'touchcancel', 'click'"));
        assertTrue(script.contains("{capture: true, passive: true}"));
        assertTrue(script.contains("setTimeout(function ()"));
        assertTrue(script.contains("' defaultPrevented=' + event.defaultPrevented"));
        assertTrue(script.contains("' reachedWindowBubble=' + bubbled.has(event)"));
        assertTrue(script.contains("document.elementsFromPoint(x, y)"));
        assertTrue(script.contains("nodes.slice(0, 4).map(describe)"));
        assertTrue(script.contains("i < 4"));
        assertTrue(script.contains("style.pointerEvents"));
        for (String forbidden : new String[]{"preventDefault(", "stopPropagation(",
                "stopImmediatePropagation(", "dispatchEvent(", ".click(", "textContent",
                "innerHTML", ".value", ".className", ".id", "location.href", "getAttribute('href')",
                "'pointermove'", "'touchmove'"}) {
            assertFalse(forbidden, script.contains(forbidden));
        }
    }

    @Test
    public void nativeDiagnosticsPreserveForwardingAndBoundMovementLogging() throws Exception {
        String source = read("main/java/com/skystream/ssmusic/PlaybackWebView.java");
        assertTrue(source.contains("boolean handled = consumed || super.dispatchTouchEvent(event);"));
        assertTrue(source.contains("return handled;"));
        assertTrue(source.contains("Logger.isEnabled() && SiteScope.isPlaybackUrl(getUrl())"));
        assertTrue(source.contains("action != MotionEvent.ACTION_MOVE"));
        assertTrue(source.contains("touchMoves++"));
        assertTrue(source.contains("\" forwarded=\" + !consumed + \" handled=\" + handled"));
        assertTrue(source.contains("synthetic ACTION_CANCEL handled="));
        assertTrue(source.contains("async hit test: "));
        assertTrue(source.contains("java.util.Objects.equals(url, getUrl())"));
        assertTrue(source.contains("MediaSwipeGesture.isFinite(fx)"));
        assertTrue(source.contains("MotionEvent.actionToString(action)"));
    }
}
