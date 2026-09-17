package com.skystream.ssmusic;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;

/** Keeps the playback renderer available when the activity's window is hidden. */
public class PlaybackWebView extends WebView {
    private final MediaSwipeGesture mediaSwipe;
    private boolean mediaSwipesEnabled;
    private boolean swipeConsumed;
    private boolean receivingSwipe;
    private int swipePointer;
    private long swipeToken;
    private String swipeUrl;
    private long touchSequence;
    private int touchMoves;

    public PlaybackWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        mediaSwipe = new MediaSwipeGesture(
                Math.max(12 * density, ViewConfiguration.get(context).getScaledTouchSlop()),
                60 * density);
    }

    void setMediaSwipesEnabled(boolean enabled) {
        mediaSwipesEnabled = enabled;
        if (!enabled) {
            cancelMediaSwipe("disabled");
        }
    }

    void cancelMediaSwipe(String reason) {
        mediaSwipe.cancel();
        if (receivingSwipe) {
            logSwipe("Native swipe cancel: " + reason);
        }
    }

    private void logSwipe(String message) {
        if (Logger.isEnabled()) {
            Logger.debug("PlaybackWebView", message);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchSequence++;
            touchMoves = 0;
        } else if (action == MotionEvent.ACTION_MOVE) {
            touchMoves++;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            cancelMediaSwipe("new touch");
            swipeConsumed = false;
            receivingSwipe = mediaSwipesEnabled;
            swipeUrl = getUrl();
            swipePointer = event.getPointerId(0);
            if (receivingSwipe) {
                logSwipe("Native swipe start");
                if (SiteScope.isPlaybackUrl(swipeUrl) && event.getPointerCount() == 1) {
                    swipeToken = mediaSwipe.start(event.getX(), event.getY());
                    checkMediaHit(event.getX(), event.getY(), swipeToken, swipeUrl);
                } else {
                    cancelMediaSwipe("untrusted page or multiple pointers");
                }
            }
        } else if (receivingSwipe) {
            if (!SiteScope.isPlaybackUrl(getUrl()) || !java.util.Objects.equals(swipeUrl, getUrl())) {
                cancelMediaSwipe("page changed");
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                cancelMediaSwipe("Android touch cancel");
            } else if (event.getPointerCount() != 1 || event.getPointerId(0) != swipePointer
                    || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_POINTER_UP) {
                cancelMediaSwipe("multiple pointers or pointer changed");
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                boolean awaitingHit = mediaSwipe.isAwaitingEligibility();
                for (int i = 0; i < event.getHistorySize(); i++) {
                    mediaSwipe.move(event.getHistoricalX(0, i), event.getHistoricalY(0, i));
                }
                mediaSwipe.move(event.getX(), event.getY());
                if (awaitingHit && !mediaSwipe.isAwaitingEligibility()) {
                    logSwipe("Native swipe passed to WebView: hit test unresolved at drag threshold");
                }
                if (!swipeConsumed && mediaSwipe.shouldIntercept()) {
                    // Chromium must see cancellation, never an UP that could click the video.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    boolean cancelHandled = super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    swipeConsumed = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    logSwipe("Native swipe intercepted; WebView touch cancelled");
                    logSwipe("WebView touch #" + touchSequence + " synthetic ACTION_CANCEL handled="
                            + cancelHandled);
                }
            }
        }
        boolean consumed = swipeConsumed;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (receivingSwipe) {
                String direction = mediaSwipe.finish();
                logSwipe("Native swipe end: " + (direction.isEmpty()
                        ? "rejected (ineligible, pending, cancelled, axis or distance)" : direction));
                if (consumed && !direction.isEmpty()) {
                    dispatchMediaSwipe(direction, swipeToken, swipeUrl);
                }
            }
            receivingSwipe = false;
            swipeConsumed = false;
            if (consumed && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        boolean handled = consumed || super.dispatchTouchEvent(event);
        if (Logger.isEnabled() && SiteScope.isPlaybackUrl(getUrl())
                && action != MotionEvent.ACTION_MOVE) {
            logSwipe("WebView touch #" + touchSequence + " " + MotionEvent.actionToString(action)
                    + " x=" + Math.round(event.getX()) + " y=" + Math.round(event.getY())
                    + " pointers=" + event.getPointerCount() + " moves=" + touchMoves
                    + " elapsedMs=" + (event.getEventTime() - event.getDownTime())
                    + " forwarded=" + !consumed + " handled=" + handled);
            if (action == MotionEvent.ACTION_DOWN) {
                logTouchTarget(event.getX(), event.getY(), touchSequence, getUrl());
            }
        }
        return handled;
    }

    private void logTouchTarget(float x, float y, long sequence, String url) {
        float fx = MediaSwipeGesture.viewportFraction(x, getWidth(), getPaddingLeft(), getPaddingRight());
        float fy = MediaSwipeGesture.viewportFraction(y, getHeight(), getPaddingTop(), getPaddingBottom());
        if (!MediaSwipeGesture.isFinite(fx) || !MediaSwipeGesture.isFinite(fy)) {
            return;
        }
        evaluateJavascript("(function(){if(location.href!==" + MainActivity.jsStringLiteral(url)
                + "){return 'page changed';}return window.__ssmusicGestureHitTest?"
                + "window.__ssmusicGestureHitTest(" + fx + "," + fy + ")"
                + ":'gesture diagnostics unavailable';})()", result -> {
                    if (Logger.isEnabled() && java.util.Objects.equals(url, getUrl())) {
                        logSwipe("WebView touch #" + sequence + " async hit test: " + result);
                    }
                });
    }

    private void checkMediaHit(float x, float y, long token, String url) {
        float fx = MediaSwipeGesture.viewportFraction(x, getWidth(), getPaddingLeft(), getPaddingRight());
        float fy = MediaSwipeGesture.viewportFraction(y, getHeight(), getPaddingTop(), getPaddingBottom());
        if (!MediaSwipeGesture.isFinite(fx) || !MediaSwipeGesture.isFinite(fy)) {
            cancelMediaSwipe("empty viewport");
            return;
        }
        evaluateJavascript("(function(){if(location.href!==" + MainActivity.jsStringLiteral(url)
                + "){return 'rejected: page changed';}"
                + "return window.__ssmusicNativeSwipeStart?"
                + "window.__ssmusicNativeSwipeStart(" + token + "," + fx + "," + fy + ")"
                + ":'rejected: gesture script not installed';})()", result -> {
                    boolean current = java.util.Objects.equals(url, getUrl())
                            && SiteScope.isPlaybackUrl(getUrl());
                    if (mediaSwipe.resolveEligibility(token, current && "\"accepted\"".equals(result))) {
                        logSwipe("JS swipe hit test: " + result);
                    } else {
                        logSwipe("JS swipe hit test ignored (stale or cancelled): " + result);
                    }
                });
    }

    private void dispatchMediaSwipe(String direction, long token, String url) {
        if (!SiteScope.isPlaybackUrl(getUrl()) || !java.util.Objects.equals(url, getUrl())) {
            logSwipe("Native swipe action rejected: page changed");
            return;
        }
        evaluateJavascript("(function(){if(location.href!==" + MainActivity.jsStringLiteral(url)
                + "){return 'rejected: page changed';}"
                + "return window.__ssmusicNativeSwipeEnd?"
                + "window.__ssmusicNativeSwipeEnd(" + token + ",'" + direction + "')"
                + ":'rejected: gesture script not installed';})()",
                result -> logSwipe("JS swipe action: " + result));
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelMediaSwipe("view detached");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        // JavaScript visibility overrides do not prevent Chromium's native suspension.
        // Keep paused media available too, so notification Play can resume in the background.
        if (visibility == View.VISIBLE) {
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
