package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdBlockerTest {

    @Test
    public void hidesUpgradeEntriesWithoutDependingOnTheirPosition() {
        String script = MainActivity.AD_HIDING_SCRIPT;
        assertTrue(script.contains(
                "document.querySelectorAll('ytmusic-guide-entry-renderer,ytmusic-mini-guide-entry-renderer')"));
        assertTrue(script.contains("a[href*=\"music_premium\"]"));
        assertTrue(script.contains("[aria-label=\"Upgrade\"],[title=\"Upgrade\"]"));
        assertTrue(script.contains("label.textContent.trim()==='Upgrade'"));
        assertTrue(script.contains("entry.style.setProperty('display','none','important')"));
        assertFalse(script.contains("last-child"));
        assertFalse(script.contains("nth-child"));
    }

    @Test
    public void keepsUpgradeHiddenWhenSideNavigationIsRenderedLater() {
        String script = MainActivity.AD_HIDING_SCRIPT;
        assertTrue(script.contains("hideUpgrade();"));
        assertTrue(script.contains("new MutationObserver(hideUpgrade).observe(document.documentElement"));
        assertTrue(script.contains("childList:true,subtree:true,characterData:true,attributes:true"));
        assertTrue(script.contains("attributeFilter:['href','aria-label','title']"));
        assertTrue(script.contains("if(document.getElementById(id)){return true;}"));
        assertTrue(script.contains(".ytp-ad-overlay-container{display:none!important;}"));
    }

    @Test
    public void blocksKnownAdHosts() {
        assertTrue(AdBlocker.isAd("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertTrue(AdBlocker.isAd("https://foo.doubleclick.net/activity"));
    }

    @Test
    public void blocksAdPathsAndQueryParameters() {
        assertTrue(AdBlocker.isAd("https://music.youtube.com/youtubei/v1/ads?key=value"));
        assertTrue(AdBlocker.isAd("https://music.youtube.com/watch?ad_format=video"));
    }

    @Test
    public void keepsMusicAndHistoryRequests() {
        assertFalse(AdBlocker.isAd("https://music.youtube.com/watch?v=abc"));
        assertFalse(AdBlocker.isAd("https://music.youtube.com/watch?list=/ads/"));
        assertFalse(AdBlocker.isAd("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"));
        assertFalse(AdBlocker.isAd("about:blank"));
    }
}
