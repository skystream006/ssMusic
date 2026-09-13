package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrlsTest {

    @Test
    public void hostOfHandlesUserInfoAndPorts() {
        assertEquals("music.youtube.com",
                Urls.hostOf("https://viewer@music.youtube.com:443/watch"));
    }

    @Test
    public void hostOfRejectsMissingScheme() {
        assertNull(Urls.hostOf("music.youtube.com/watch"));
    }

    @Test
    public void pathOfReturnsOnlyPath() {
        assertEquals("/watch", Urls.pathOf("https://music.youtube.com/watch?list=/ads/#top"));
    }

    @Test
    public void pathOfReturnsEmptyWhenNoPathExists() {
        assertEquals("", Urls.pathOf("https://music.youtube.com?feature=home"));
    }

    @Test
    public void isAllowedThumbnailHostAllowsYoutubeImageHosts() {
        assertTrue(Urls.isAllowedThumbnailHost("i.ytimg.com"));
        assertTrue(Urls.isAllowedThumbnailHost("yt3.ggpht.com"));
    }

    @Test
    public void isAllowedThumbnailHostRejectsLookalikeHosts() {
        assertFalse(Urls.isAllowedThumbnailHost("ytimg.com.evil.example"));
        assertFalse(Urls.isAllowedThumbnailHost(null));
    }

    @Test
    public void isAllowedHttpsThumbnailUrlRequiresHttpsAllowedHost() {
        assertTrue(Urls.isAllowedHttpsThumbnailUrl("https://i.ytimg.com/vi/id/default.jpg"));
        assertFalse(Urls.isAllowedHttpsThumbnailUrl("http://i.ytimg.com/vi/id/default.jpg"));
        assertFalse(Urls.isAllowedHttpsThumbnailUrl("https://ytimg.com.evil.example/image.jpg"));
        assertFalse(Urls.isAllowedHttpsThumbnailUrl("not a url"));
    }

    @Test
    public void sanitizeHttpsThumbnailUrlTrimsAndEnforcesLength() {
        assertEquals("https://i.ytimg.com/image.jpg",
                Urls.sanitizeHttpsThumbnailUrl(" https://i.ytimg.com/image.jpg ", 100));
        assertNull(Urls.sanitizeHttpsThumbnailUrl("https://i.ytimg.com/image.jpg", 10));
    }
}
