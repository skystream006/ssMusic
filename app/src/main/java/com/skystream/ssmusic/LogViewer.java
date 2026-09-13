package com.skystream.ssmusic;

import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Displays a selectable snapshot of the private diagnostic log. */
final class LogViewer {
    private LogViewer() {
    }

    static void show(AppCompatActivity activity) {
        TextView text = new TextView(activity);
        text.setTextIsSelectable(true);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(12);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.log_share_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.refresh, null)
                .create();
        Runnable refresh = () -> {
            text.setText(R.string.log_loading);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            Logger.readCurrentLog(activity, (snapshot, error) -> activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) {
                    return;
                }
                if (error != null) {
                    text.setText(R.string.log_read_failed);
                } else {
                    text.setText(snapshot.isEmpty() ? activity.getString(R.string.log_empty) : snapshot);
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
            }));
        };
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> refresh.run());
            refresh.run();
        });
        dialog.show();
    }
}
