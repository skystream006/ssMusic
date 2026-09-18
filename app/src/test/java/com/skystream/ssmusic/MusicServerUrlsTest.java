package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MusicServerUrlsTest {
    private static final String SONG = "https://music.youtube.com/watch?v=abcdefghijk";

    @Test
    public void sendsCurrentPlaylistUrlUnchanged() {
        String url = SONG + "&list=PL123&index=3&start_radio=1";
        assertEquals(url, MusicServerUrls.playlistUrl(url));
        String playlist = "https://music.youtube.com/playlist?list=PL123";
        assertEquals(playlist, MusicServerUrls.playlistUrl(playlist));
    }

    @Test
    public void stripsAllPlaylistAndTrackingParametersForSong() {
        assertEquals(SONG, MusicServerUrls.songUrl(SONG
                + "&list=PL123&index=3&start_radio=1&pp=value&si=tracking#fragment"));
        assertEquals(SONG, MusicServerUrls.songUrl(SONG));
        assertEquals(SONG, MusicServerUrls.songUrl(
                "https://music.youtube.com/watch?list=PL123&%76=abcdefghijk"));
    }

    @Test
    public void playlistButtonRequiresNonemptyUnambiguousListParameter() {
        assertNull(MusicServerUrls.playlistUrl(null));
        assertNull(MusicServerUrls.playlistUrl(SONG));
        assertNull(MusicServerUrls.playlistUrl(SONG + "#list=PL123"));
        assertNull(MusicServerUrls.playlistUrl(SONG + "&list="));
        assertNull(MusicServerUrls.playlistUrl(SONG + "&list=PL1&list=PL2"));
        assertEquals(SONG + "&%6cist=PL123",
                MusicServerUrls.playlistUrl(SONG + "&%6cist=PL123"));
    }

    @Test
    public void rejectsNonMusicAndMalformedUrls() {
        String[] urls = {
                "http://music.youtube.com/watch?v=abcdefghijk&list=PL1",
                "https://music.youtube.com.evil.test/watch?v=abcdefghijk&list=PL1",
                "https://user@music.youtube.com/watch?v=abcdefghijk&list=PL1",
                "https://music.youtube.com:8443/watch?v=abcdefghijk&list=PL1",
                "https://accounts.google.com/watch?v=abcdefghijk&list=PL1",
                "https://music.youtube.com/watch?v=%zz&list=%zz"
        };
        for (String url : urls) {
            assertNull(MusicServerUrls.playlistUrl(url));
            assertNull(MusicServerUrls.songUrl(url));
        }
    }

    @Test
    public void noSongOnPlaylistOrHomePageAndRejectsInvalidIds() {
        assertNull(MusicServerUrls.songUrl("https://music.youtube.com/playlist?list=PL1"));
        assertNull(MusicServerUrls.songUrl("https://music.youtube.com/"));
        assertNull(MusicServerUrls.songUrl(SONG + "&v=otherSongId"));
        assertNull(MusicServerUrls.songUrlForId("abc&list=PL1"));
        assertNull(MusicServerUrls.songUrlForId(""));
        assertNull(MusicServerUrls.songUrlForId(null));
        assertEquals(SONG, MusicServerUrls.songUrlForId("abcdefghijk"));
    }
}
