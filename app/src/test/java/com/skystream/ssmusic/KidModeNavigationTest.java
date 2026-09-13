package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KidModeNavigationTest {
    @Test
    public void allowsLibraryAndExplicitPlaylistRoutes() {
        for (String path : new String[]{"/library", "/library/playlists", "/playlist?list=PL_test-1",
                "/watch?v=track-1&list=PL_test-1", "/watch?list=LM&v=track_2"}) {
            assertTrue(path, KidModeNavigation.isAllowed("https://music.youtube.com" + path));
        }
    }

    @Test
    public void rejectsDiscoveryRadioAndStandaloneTracks() {
        for (String path : new String[]{"", "/", "/explore", "/search?q=song", "/browse/artist",
                "/watch?v=track", "/watch?list=PL_test", "/watch?v=track&list=",
                "/watch?v=track&list=RDAMVMtrack", "/playlist?list=RDCLAKtest",
                "/playlist?list=%52Dtest", "/playlist?list=PL%26list%3DRDtest",
                "/library-other", "/library/../search", "/%6cibrary"}) {
            assertFalse(path, KidModeNavigation.isAllowed("https://music.youtube.com" + path));
        }
    }

    @Test
    public void rejectsUntrustedOriginsAndMalformedUrls() {
        for (String url : new String[]{null, "", "not a URL", "javascript:alert(1)",
                "http://music.youtube.com/library", "https://youtube.com/library",
                "https://accounts.google.com/library", "https://music.youtube.com.evil.test/library",
                "https://user@music.youtube.com/library", "https://music.youtube.com:444/library"}) {
            assertFalse(String.valueOf(url), KidModeNavigation.isAllowed(url));
        }
    }
}
