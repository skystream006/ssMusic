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
    public void createsPlaybackIdentityFromVideoAndPlaylist() {
        assertEquals("https://music.youtube.com/watch?v=song123&list=playlist456",
                Preferences.playbackIdentityUrl(
                        "https://music.youtube.com/watch?list=playlist456&v=song123&t=42&feature=share"));
    }

    @Test
    public void createsPlaybackUrlWithTimestamp() {
        assertEquals("https://music.youtube.com/watch?v=song123&list=playlist456&t=120",
                Preferences.playbackUrlWithTimestamp(
                        "https://music.youtube.com/watch?v=song123&list=playlist456&t=5",
                        120.75d));
    }

    @Test
    public void comparesPlaybackItemsWithoutTimestamp() {
        assertTrue(Preferences.isSamePlaybackItem(
                "https://music.youtube.com/watch?v=song123&list=playlist456&t=5",
                "https://music.youtube.com/watch?list=playlist456&v=song123&t=120"));
    }

    @Test
    public void fallsBackHomeForMissingOrUntrustedLocation() {
        assertEquals(Preferences.homeUrl(), Preferences.restoreUrl(null));
        assertEquals(Preferences.homeUrl(),
                Preferences.restoreUrl("https://accounts.google.com/signin"));
        assertEquals(Preferences.homeUrl(), Preferences.restoreUrl("https://example.com/"));
    }
}
