package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaSwipeGestureTest {
    private MediaSwipeGesture eligibleGesture() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        assertTrue(gesture.resolveEligibility(token, true));
        return gesture;
    }

    @Test
    public void mapsAllFourDirectionsAfterMinimumDistance() {
        float[][] ends = {{40, 100}, {160, 100}, {100, 40}, {100, 160}};
        String[] actions = {"left", "right", "up", "down"};
        for (int i = 0; i < actions.length; i++) {
            MediaSwipeGesture gesture = eligibleGesture();
            gesture.move(ends[i][0], ends[i][1]);
            assertTrue(gesture.shouldIntercept());
            assertEquals(actions[i], gesture.finish());
            assertEquals("", gesture.finish());
        }
    }

    @Test
    public void tapsAndSubThresholdMovesAreNotIntercepted() {
        MediaSwipeGesture gesture = eligibleGesture();
        gesture.move(111, 102);
        assertFalse(gesture.shouldIntercept());
        assertEquals("", gesture.finish());
    }

    @Test
    public void interceptionDoesNotDispatchShortSwipe() {
        MediaSwipeGesture gesture = eligibleGesture();
        gesture.move(112, 100);
        assertTrue(gesture.shouldIntercept());
        gesture.move(159, 100);
        assertEquals("", gesture.finish());
    }

    @Test
    public void thresholdsUseViewDensityRatherThanCssScale() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(36, 180);
        long token = gesture.start(300, 300);
        gesture.resolveEligibility(token, true);
        gesture.move(335, 300);
        assertFalse(gesture.shouldIntercept());
        gesture.move(336, 300);
        assertTrue(gesture.shouldIntercept());
        gesture.move(480, 300);
        assertEquals("right", gesture.finish());
    }

    @Test
    public void diagonalGestureDoesNotBecomeSwipeLater() {
        MediaSwipeGesture gesture = eligibleGesture();
        gesture.move(120, 120);
        assertFalse(gesture.shouldIntercept());
        gesture.move(220, 120);
        assertEquals("", gesture.finish());
    }

    @Test
    public void directionMustDominateOtherAxis() {
        assertEquals("", MediaSwipeGesture.direction(75, 60, 60));
        assertEquals("", MediaSwipeGesture.direction(60, 75, 60));
        assertEquals("right", MediaSwipeGesture.direction(76, 60, 60));
        assertEquals("up", MediaSwipeGesture.direction(60, -76, 60));
    }

    @Test
    public void wrongAxisAndReversalPermanentlyCancelLockedSwipe() {
        for (float[] point : new float[][]{{100, 180}, {80, 100}, {150, 150}}) {
            MediaSwipeGesture gesture = eligibleGesture();
            gesture.move(120, 100);
            assertTrue(gesture.shouldIntercept());
            gesture.move(point[0], point[1]);
            gesture.move(200, 100);
            assertFalse(gesture.shouldIntercept());
            assertEquals("", gesture.finish());
        }
    }

    @Test
    public void cancellationForMultitouchNavigationOrAndroidCancelCannotBeRevived() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        gesture.resolveEligibility(token, true);
        gesture.move(200, 100);
        gesture.cancel();
        assertFalse(gesture.resolveEligibility(token, true));
        gesture.move(300, 100);
        assertFalse(gesture.shouldIntercept());
        assertEquals("", gesture.finish());
    }

    @Test
    public void pendingHitTestCanResolveBeforeDirectionalThreshold() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        gesture.move(111, 100);
        assertFalse(gesture.shouldIntercept());
        assertTrue(gesture.isAwaitingEligibility());
        assertTrue(gesture.resolveEligibility(token, true));
        gesture.move(200, 100);
        assertTrue(gesture.shouldIntercept());
        assertEquals("right", gesture.finish());
    }

    @Test
    public void lateHitTestCannotTakeOverDragAlreadyPassedToWebView() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        gesture.move(112, 100);
        assertFalse(gesture.shouldIntercept());
        assertFalse(gesture.isAwaitingEligibility());
        assertFalse(gesture.resolveEligibility(token, true));
        gesture.move(200, 100);
        assertEquals("", gesture.finish());
    }

    @Test
    public void pendingHitTestAtReleaseCannotDispatchLateAction() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        gesture.move(111, 100);
        assertTrue(gesture.isAwaitingEligibility());
        assertEquals("", gesture.finish());
        assertFalse(gesture.resolveEligibility(token, true));
        assertFalse(gesture.shouldIntercept());
    }

    @Test
    public void staleHitTestCannotAuthorizeNextTouch() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long oldToken = gesture.start(100, 100);
        long newToken = gesture.start(100, 100);
        assertFalse(gesture.resolveEligibility(oldToken, true));
        gesture.move(111, 100);
        assertFalse(gesture.shouldIntercept());
        assertTrue(gesture.resolveEligibility(newToken, true));
        gesture.move(200, 100);
        assertEquals("right", gesture.finish());
    }

    @Test
    public void rejectedHitTestCannotTriggerAction() {
        MediaSwipeGesture gesture = new MediaSwipeGesture(12, 60);
        long token = gesture.start(100, 100);
        gesture.move(111, 100);
        assertTrue(gesture.resolveEligibility(token, false));
        assertFalse(gesture.resolveEligibility(token, true));
        assertFalse(gesture.shouldIntercept());
        assertEquals("", gesture.finish());
    }

    @Test
    public void newTouchResetsDirectionAndRequiresFreshEligibility() {
        MediaSwipeGesture gesture = eligibleGesture();
        gesture.move(180, 100);
        assertEquals("right", gesture.finish());
        long token = gesture.start(200, 200);
        gesture.move(200, 190);
        assertFalse(gesture.shouldIntercept());
        gesture.resolveEligibility(token, true);
        gesture.move(200, 120);
        assertEquals("up", gesture.finish());
    }

    @Test
    public void nonFiniteMotionCannotBeRecognized() {
        MediaSwipeGesture gesture = eligibleGesture();
        gesture.move(Float.NaN, 200);
        assertEquals("", gesture.finish());
        assertEquals("", MediaSwipeGesture.direction(Float.POSITIVE_INFINITY, 0, 60));
    }

    @Test
    public void viewCoordinatesNormalizeAcrossSizesAndPadding() {
        assertEquals(0.5f, MediaSwipeGesture.viewportFraction(540, 1080, 0, 0), 0.0001f);
        assertEquals(0.5f, MediaSwipeGesture.viewportFraction(180, 360, 0, 0), 0.0001f);
        assertEquals(0.5f, MediaSwipeGesture.viewportFraction(550, 1100, 10, 10), 0.0001f);
        assertEquals(0f, MediaSwipeGesture.viewportFraction(10, 1100, 10, 10), 0.0001f);
        assertEquals(1f, MediaSwipeGesture.viewportFraction(1090, 1100, 10, 10), 0.0001f);
        assertTrue(Float.isNaN(MediaSwipeGesture.viewportFraction(0, 0, 0, 0)));
    }
}
