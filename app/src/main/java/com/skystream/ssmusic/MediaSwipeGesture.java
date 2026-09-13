package com.skystream.ssmusic;

/** Single-pointer recognition independent of Android events and asynchronous page hit tests. */
final class MediaSwipeGesture {
    private final float interceptDistance;
    private final float actionDistance;
    private long generation;
    private boolean active;
    private boolean eligible;
    private float startX;
    private float startY;
    private float dx;
    private float dy;
    private String direction = "";

    MediaSwipeGesture(float interceptDistance, float actionDistance) {
        this.interceptDistance = interceptDistance;
        this.actionDistance = actionDistance;
    }

    long start(float x, float y) {
        generation++;
        active = true;
        eligible = false;
        startX = x;
        startY = y;
        dx = dy = 0;
        direction = "";
        return generation;
    }

    boolean resolveEligibility(long token, boolean accepted) {
        if (!active || token != generation) {
            return false;
        }
        eligible = accepted;
        if (!accepted) {
            cancel();
        }
        return true;
    }

    void move(float x, float y) {
        if (!active) {
            return;
        }
        dx = x - startX;
        dy = y - startY;
        if (!isFinite(dx) || !isFinite(dy)) {
            cancel();
            return;
        }
        String next = direction(dx, dy, interceptDistance);
        if (Math.max(Math.abs(dx), Math.abs(dy)) >= interceptDistance) {
            // Once a drag has reached the page, a delayed hit test must not take it over.
            if (!eligible || next.isEmpty() || (!direction.isEmpty() && !direction.equals(next))) {
                cancel();
            } else {
                direction = next;
            }
        }
    }

    boolean shouldIntercept() {
        return active && eligible && !direction.isEmpty();
    }

    boolean isAwaitingEligibility() {
        return active && !eligible;
    }

    String finish() {
        String action = shouldIntercept() && direction.equals(direction(dx, dy, actionDistance))
                ? direction : "";
        cancel();
        return action;
    }

    void cancel() {
        active = false;
        eligible = false;
    }

    static String direction(float dx, float dy, float threshold) {
        if (!isFinite(dx) || !isFinite(dy)) {
            return "";
        }
        if (Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.25f) {
            return dx < 0 ? "left" : "right";
        }
        if (Math.abs(dy) >= threshold && Math.abs(dy) > Math.abs(dx) * 1.25f) {
            return dy < 0 ? "up" : "down";
        }
        return "";
    }

    static float viewportFraction(float coordinate, int size, int leadingPadding,
            int trailingPadding) {
        int contentSize = size - leadingPadding - trailingPadding;
        return contentSize > 0 ? (coordinate - leadingPadding) / contentSize : Float.NaN;
    }

    static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
