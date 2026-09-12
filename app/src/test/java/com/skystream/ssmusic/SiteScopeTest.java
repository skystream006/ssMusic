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
        assertTrue(SiteScope.isInAppUrl("https://accounts.youtube.com/accounts/SetSID"));
        assertTrue(SiteScope.isInAppUrl("https://myaccount.google.com/"));
        assertTrue(SiteScope.isInAppUrl("https://ogs.google.com/widget/app"));
        assertTrue(SiteScope.isInAppUrl("https://www.youtube.com/signin"));
        assertTrue(SiteScope.isInAppUrl("https://lh3.googleusercontent.com/avatar"));
    }

    @Test
    public void identifiesGoogleAccountUrls() {
        assertTrue(SiteScope.isGoogleAccountUrl("https://accounts.google.com/signin"));
        assertTrue(SiteScope.isGoogleAccountUrl("https://accounts.youtube.com/accounts/SetSID"));
        assertFalse(SiteScope.isGoogleAccountUrl("https://music.youtube.com/"));
        assertFalse(SiteScope.isGoogleAccountUrl("https://www.youtube.com/signin"));
    }

    @Test
    public void blocksOutOfScopeHosts() {
        assertFalse(SiteScope.isInAppUrl("https://example.com/"));
        assertFalse(SiteScope.isInAppUrl("https://www.google.com/search?q=music"));
        assertNull(SiteScope.normalizeInAppUrl("javascript:alert(1)"));
    }
}
