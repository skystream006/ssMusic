package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteScopeTest {

    @Test
    public void keepsYoutubeMusicInApp() {
        assertTrue(SiteScope.isInAppUrl("https://music.youtube.com/playlist?list=abc"));
        assertEquals("https://music.youtube.com/",
                SiteScope.normalizeInAppUrl("http://music.youtube.com/"));
    }

    @Test
    public void allowsGoogleSignInDependencies() {
        assertTrue(SiteScope.isInAppUrl("https://accounts.google.com/signin"));
        assertTrue(SiteScope.isInAppUrl("https://lh3.googleusercontent.com/avatar"));
    }

    @Test
    public void blocksOutOfScopeHosts() {
        assertFalse(SiteScope.isInAppUrl("https://example.com/"));
        assertNull(SiteScope.normalizeInAppUrl("javascript:alert(1)"));
    }
}
