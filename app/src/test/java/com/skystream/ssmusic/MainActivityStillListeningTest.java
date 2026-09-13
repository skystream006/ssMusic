package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Structural regression checks; the Android unit-test runtime has no JavaScript engine. */
public class MainActivityStillListeningTest {
    @Test
    public void confirmsOnlyTheMusicInactivityPromptWithoutDependingOnLanguage() {
        String script = MainActivity.STILL_LISTENING_SCRIPT;
        assertTrue(script.contains("location.origin!=='https://music.youtube.com'"));
        assertTrue(script.contains("window!==window.top"));
        assertTrue(script.contains("var PROMPT='ytmusic-you-there-renderer'"));
        assertTrue(script.contains("document.querySelectorAll(PROMPT)"));
        assertTrue(script.contains("prompt.querySelector('[dialog-confirm] button"));
        assertTrue(script.contains("button.click()"));
        assertFalse(script.contains("yt-confirm-dialog-renderer"));
        assertFalse(script.contains("textContent"));
        assertFalse(script.contains(".play("));
        assertFalse(MainActivity.AD_HIDING_SCRIPT.contains("ytmusic-you-there-renderer"));
    }

    @Test
    public void ignoresHiddenAndDisabledControls() {
        String script = MainActivity.STILL_LISTENING_SCRIPT;
        assertTrue(script.contains("document.documentElement.contains(node)"));
        assertTrue(script.contains("[hidden],[aria-hidden=\"true\"],dialog:not([open])"));
        assertTrue(script.contains("style.visibility!=='hidden'"));
        assertTrue(script.contains("node.getClientRects().length>0"));
        assertTrue(script.contains("if(confirmed.has(prompt)||!visible(prompt)){continue;}"));
        assertTrue(script.contains("if(!button||!visible(button)"));
        assertTrue(script.contains("[disabled],[aria-disabled=\"true\"],[inert]"));
    }

    @Test
    public void observesLateRenderingAndPlaybackWithoutPolling() {
        String script = MainActivity.STILL_LISTENING_SCRIPT;
        assertTrue(script.contains("if(window.__ssmusicStillListeningObserver||!document.documentElement){return;}"));
        assertTrue(script.contains("new MutationObserver(function(changes)"));
        assertTrue(script.contains("childList:true,subtree:true,attributes:true"));
        assertTrue(script.contains("attributeOldValue:true"));
        assertTrue(script.contains("'disabled','aria-disabled'"));
        assertTrue(script.contains("document.addEventListener('pause',confirm,true)"));
        assertTrue(script.contains("document.addEventListener('visibilitychange',confirm)"));
        assertTrue(script.contains("document.addEventListener('yt-navigate-finish',confirm)"));
        assertTrue(script.endsWith("confirm();})()"));
        assertFalse(script.contains("setInterval"));
        assertFalse(script.contains("requestAnimationFrame"));
    }

    @Test
    public void allowsReusedPromptsButDoesNotClickRepeatedlyWhileOpen() {
        String script = MainActivity.STILL_LISTENING_SCRIPT;
        assertTrue(script.contains("if(!visible(prompt)){confirmed.delete(prompt);}"));
        assertTrue(script.contains("change.attributeName==='hidden'&&change.oldValue!==null"));
        assertTrue(script.contains("change.attributeName==='aria-hidden'&&change.oldValue==='true'"));
        assertTrue(script.contains("change.attributeName==='open'&&change.oldValue===null"));
        assertTrue(script.contains("if(change.target.contains(prompt)){confirmed.delete(prompt);}"));
        assertFalse(script.contains("removedNodes"));
        assertTrue(script.contains("confirmed.has(prompt)"));
        assertTrue(script.indexOf("confirmed.add(prompt)") < script.indexOf("button.click()"));
    }
}
