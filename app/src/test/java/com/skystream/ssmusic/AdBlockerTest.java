package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdBlockerTest {

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
        assertFalse(AdBlocker.isAd("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"));
        assertFalse(AdBlocker.isAd("about:blank"));
    }
}
