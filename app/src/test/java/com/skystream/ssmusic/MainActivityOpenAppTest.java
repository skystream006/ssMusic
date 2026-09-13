package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainActivityOpenAppTest {
    @Test
    public void targetsOpenAppControlsOnlyWithinNavigation() {
        String script = MainActivity.OPEN_APP_HIDING_SCRIPT;
        assertTrue(script.contains("var NAV='ytmusic-nav-bar,ytmusic-guide-renderer,ytmusic-mini-guide-renderer'"));
        assertTrue(script.contains("document.querySelectorAll(NAV)"));
        assertTrue(script.contains("navs[i].querySelectorAll(CONTROLS)"));
        assertTrue(script.contains("node.classList.contains('app-install-link')"));
        assertTrue(script.contains("node.querySelector('.app-install-link')"));
        assertTrue(script.contains("/^open app$/i"));
        assertTrue(script.contains("node.getAttribute('aria-label')"));
        assertTrue(script.contains("replace(/\\s+/g,' ').trim()"));
        assertTrue(script.contains("if(!isOpenApp(node)){continue;}"));
    }

    @Test
    public void hidesEntireSideNavigationEntryWithoutLeavingAnEmptyButton() {
        String script = MainActivity.OPEN_APP_HIDING_SCRIPT;
        assertTrue(script.contains("node.closest('ytmusic-guide-entry-renderer,ytmusic-mini-guide-entry-renderer')"));
        assertTrue(script.contains("(entry||node).style.setProperty('display','none','important')"));
    }

    @Test
    public void handlesLateRenderingWithoutDuplicateObserversOrStyleMutationLoops() {
        String script = MainActivity.OPEN_APP_HIDING_SCRIPT;
        assertTrue(script.contains("hide();"));
        assertTrue(script.contains("if(!window.__ssmusicOpenAppObserver&&document.documentElement)"));
        assertTrue(script.contains("new MutationObserver(hide)"));
        assertTrue(script.contains("childList:true,subtree:true,characterData:true,attributes:true"));
        assertTrue(script.contains("attributeFilter:['class','aria-label']"));
        assertFalse(script.contains("setInterval("));
    }
}
