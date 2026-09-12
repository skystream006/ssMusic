package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
