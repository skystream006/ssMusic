package com.skystream.ssmusic;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

/** Keeps the playback renderer available when the activity's window is hidden. */
public class PlaybackWebView extends WebView {

    public PlaybackWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
