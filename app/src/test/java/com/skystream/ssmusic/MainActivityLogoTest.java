package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainActivityLogoTest {

    @Test
    public void replacesYoutubeMusicLogoWithAppIcon() {
        assertTrue(MainActivity.APP_LOGO_SCRIPT.contains("ytmusic-logo"));
        assertTrue(MainActivity.APP_LOGO_SCRIPT.contains(MainActivity.APP_LOGO_PATH));
        assertTrue(MainActivity.APP_LOGO_SCRIPT.contains("MutationObserver(schedule)"));
        assertTrue(MainActivity.APP_LOGO_SCRIPT.contains("requestAnimationFrame"));
    }

    @Test
    public void servesTheIconOnlyFromYoutubeMusic() {
        assertTrue(MainActivity.isAppLogoRequest(
                "https://music.youtube.com/ssmusic_app_logo.png"));
        assertTrue(MainActivity.isAppLogoRequest(
                "https://music.youtube.com/ssmusic_app_logo.png?cache=1"));
        assertFalse(MainActivity.isAppLogoRequest(
                "https://www.music.youtube.com/ssmusic_app_logo.png"));
        assertFalse(MainActivity.isAppLogoRequest(
                "http://music.youtube.com/ssmusic_app_logo.png"));
        assertFalse(MainActivity.isAppLogoRequest(
                "https://music.youtube.com/other/ssmusic_app_logo.png"));
    }
}
