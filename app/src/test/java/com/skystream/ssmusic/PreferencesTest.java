package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreferencesTest {

    @Test
    public void homeUrlIsYoutubeMusic() {
        assertEquals("https://music.youtube.com/", Preferences.homeUrl());
    }

    @Test
    public void userAgentSwitchesBetweenMobileAndDesktop() {
        assertTrue(Preferences.userAgent(false).contains("Mobile"));
        assertTrue(Preferences.userAgent(true).contains("X11"));
    }

    @Test
    public void restoresYoutubeMusicLocation() {
        String currentSong = "https://music.youtube.com/watch?v=song123&list=playlist456";
        assertEquals(currentSong, Preferences.restoreUrl(currentSong));
    }

    @Test
    public void fallsBackHomeForMissingOrUntrustedLocation() {
        assertEquals(Preferences.homeUrl(), Preferences.restoreUrl(null));
        assertEquals(Preferences.homeUrl(),
                Preferences.restoreUrl("https://accounts.google.com/signin"));
        assertEquals(Preferences.homeUrl(), Preferences.restoreUrl("https://example.com/"));
    }
}
