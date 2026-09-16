package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SongRefreshCounterTest {
    private String id(int number) {
        return String.format(java.util.Locale.US, "song%07d", number);
    }

    @Test
    public void refreshesAtSixthStartAndResetsForAnotherSixStarts() {
        SongRefreshCounter counter = new SongRefreshCounter();
        for (int i = 1; i <= 5; i++) {
            assertFalse(counter.songStarted(id(i)));
        }
        assertTrue(counter.songStarted(id(6)));
        counter.reset(); // Page reload retains the sixth song's identity.
        assertFalse(counter.songStarted(id(6)));
        for (int i = 7; i <= 11; i++) {
            assertFalse(counter.songStarted(id(i)));
        }
        assertTrue(counter.songStarted(id(12)));
    }

    @Test
    public void interactionsResetCountWithoutRecountingTheCurrentSong() {
        SongRefreshCounter counter = new SongRefreshCounter();
        for (int i = 1; i <= 5; i++) {
            assertFalse(counter.songStarted(id(i)));
        }
        counter.reset();
        counter.reset();
        assertFalse(counter.songStarted(id(5)));
        for (int i = 6; i <= 10; i++) {
            assertFalse(counter.songStarted(id(i)));
        }
        assertTrue(counter.songStarted(id(11)));
    }

    @Test
    public void duplicateReportsAndPauseResumeDoNotCountAgain() {
        SongRefreshCounter counter = new SongRefreshCounter();
        for (int i = 1; i <= 5; i++) {
            for (int duplicate = 0; duplicate < 10; duplicate++) {
                assertFalse(counter.songStarted(id(i)));
            }
        }
        assertTrue(counter.songStarted(id(6)));
    }

    @Test
    public void repeatAfterEndingCountsAsANewStart() {
        SongRefreshCounter counter = new SongRefreshCounter();
        for (int i = 0; i < 5; i++) {
            assertFalse(counter.songStarted(id(1)));
            counter.songEnded(id(1));
        }
        assertTrue(counter.songStarted(id(1)));
    }

    @Test
    public void ignoresInvalidIdsAndStaleEndedEvents() {
        SongRefreshCounter counter = new SongRefreshCounter();
        assertFalse(counter.songStarted(null));
        assertFalse(counter.songStarted(""));
        assertFalse(counter.songStarted("invalid"));
        assertFalse(counter.songStarted("bad/id00001"));
        for (int i = 1; i <= 5; i++) {
            assertFalse(counter.songStarted(id(i)));
            counter.songEnded(id(i + 1));
            counter.songEnded(null);
            assertFalse(counter.songStarted(id(i)));
        }
        assertTrue(counter.songStarted(id(6)));
    }
}
