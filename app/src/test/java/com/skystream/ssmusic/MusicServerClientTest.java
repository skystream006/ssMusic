package com.skystream.ssmusic;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MusicServerClientTest {
    @Test
    public void boundsServerResponseReads() throws Exception {
        String json = "{\"session\":{}}";
        assertEquals(json, MusicServerClient.readBounded(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Long.MAX_VALUE));
        assertEquals(65536, MusicServerClient.readBounded(
                new ByteArrayInputStream(new byte[65536]), Long.MAX_VALUE).length());
        try {
            MusicServerClient.readBounded(
                    new ByteArrayInputStream(new byte[65537]), Long.MAX_VALUE);
            fail("Accepted oversized response");
        } catch (IOException expected) {
            assertEquals("Server response exceeded limits.", expected.getMessage());
        }
    }

    @Test
    public void rejectsResponsesPastDeadline() {
        try {
            MusicServerClient.readBounded(new ByteArrayInputStream(new byte[0]), System.nanoTime() - 1);
            fail("Accepted expired response");
        } catch (IOException expected) {
            assertEquals("Server response timed out.", expected.getMessage());
        }
    }

    @Test
    public void validatesJobUrlsBeforeSending() {
        assertTrue(MusicServerClient.validJobUrl("https://music.youtube.com/watch?v=abc"));
        assertTrue(MusicServerClient.validJobUrl("https://music.youtube.com:443/playlist?list=PL1"));
        for (String value : new String[]{null, "", "javascript:alert(1)", "file:///music",
                "https://", "http://music.youtube.com/watch?v=abc", "https://example.com/music",
                "https://www.youtube.com/watch?v=abc", "https://music.youtube.com.evil.test/watch",
                "https://" + "user@music.youtube.com/watch", "https://music.youtube.com:8443/watch",
                "https://music.youtube.com:0", "https://music.youtube.com:65536",
                "https://music.youtube.com:", "https://music.youtube.com:0443/watch",
                "https://music.youtube.com/\n"}) {
            assertFalse(MusicServerClient.validJobUrl(value));
        }
    }

    @Test
    public void sessionRejectsHeaderInjectionAndInvalidOrigins() throws Exception {
        MusicServerStore.Session session =
                new MusicServerStore.Session("https://Music.Example.com/", "safe_token", 1000);
        assertEquals("https://music.example.com", session.origin);
        for (String token : new String[]{"", "token\r\nOther: value", "token value", "token\u0000"}) {
            try {
                new MusicServerStore.Session("https://music.example.com", token, 1000);
                fail("Accepted invalid bearer token");
            } catch (Exception expected) {
                assertEquals("Invalid server session.", expected.getMessage());
            }
        }
    }
}
