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
}
