package com.skystream.ssmusic;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

final class MusicServerPreferences {
    static final String CURRENT_SONG_SCRIPT = "(function(){"
            + "if(location.origin!=='https://music.youtube.com'){return null;}"
            + "var player=document.querySelector('#movie_player');"
            + "var media=player&&player.querySelector('video,audio');"
            + "if(!media||media.ended||player.classList.contains('ad-showing')){return null;}"
            + "try{var data=player.getVideoData();"
            + "return data&&/^[A-Za-z0-9_-]{11}$/.test(data.video_id)?data.video_id:null;"
            + "}catch(e){return null;}})()";

    private final MainActivity activity;
    private final PlaybackWebView webView;
    private final MusicServerClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View panel;
    private AlertDialog loginDialog;
    private boolean resolvingSong;
    private boolean destroyed;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            if (panel != null) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    MusicServerPreferences(MainActivity activity, PlaybackWebView webView) {
        this.activity = activity;
        this.webView = webView;
        client = MusicServerClient.get(activity);
    }

    void bind(View content, AlertDialog dialog) {
        panel = content;
        content.findViewById(R.id.music_server_login).setOnClickListener(v -> showLogin());
        content.findViewById(R.id.music_server_cancel).setOnClickListener(v -> {
            client.cancelLogin();
            update();
        });
        content.findViewById(R.id.music_server_logout).setOnClickListener(v -> {
            client.logout();
            update();
        });
        content.findViewById(R.id.music_server_playlist).setOnClickListener(v -> {
            String url = MusicServerUrls.playlistUrl(webView.getUrl());
            if (url == null) {
                showMessage(activity.getString(R.string.music_server_no_playlist));
                update();
            } else {
                client.submitJob(url, this::completed);
                update();
            }
        });
        content.findViewById(R.id.music_server_song).setOnClickListener(v -> sendSong());
        dialog.setOnDismissListener(d -> {
            if (panel == content) {
                panel = null;
                handler.removeCallbacks(refresh);
            }
        });
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    boolean handleIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"com.ssytdlp.app".equals(data.getScheme())) {
            return false;
        }
        String callback = data.toString();
        // Never retain callback codes in the Activity intent or pass them to the WebView/logger.
        intent.setData(null);
        client.completeLogin(callback, this::completed);
        update();
        return true;
    }

    void destroy() {
        destroyed = true;
        panel = null;
        handler.removeCallbacksAndMessages(null);
        if (loginDialog != null) {
            loginDialog.dismiss();
        }
    }

    private void update() {
        if (panel == null || destroyed) {
            return;
        }
        boolean loggedIn = client.isLoggedIn();
        boolean pending = client.hasPendingLogin();
        boolean busy = client.isBusy() || resolvingSong;
        TextView status = panel.findViewById(R.id.music_server_status);
        status.setText(busy ? activity.getString(R.string.music_server_working)
                : loggedIn ? activity.getString(R.string.music_server_connected, client.getServerOrigin())
                : pending ? activity.getString(R.string.music_server_login_pending)
                : activity.getString(R.string.music_server_summary));
        setButton(R.id.music_server_login, !loggedIn && !pending, !busy);
        setButton(R.id.music_server_cancel, pending, !busy);
        setButton(R.id.music_server_logout, loggedIn, !busy);
        setButton(R.id.music_server_playlist,
                loggedIn && MusicServerUrls.playlistUrl(webView.getUrl()) != null, !busy);
        setButton(R.id.music_server_song, loggedIn, !busy && SiteScope.isPlaybackUrl(webView.getUrl()));
    }

    private void setButton(int id, boolean visible, boolean enabled) {
        View button = panel.findViewById(id);
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setEnabled(enabled);
    }

    private void showLogin() {
        EditText origin = new EditText(activity);
        origin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        origin.setSingleLine(true);
        origin.setHint(R.string.music_server_origin_hint);
        origin.setContentDescription(activity.getString(R.string.music_server_origin));
        origin.setText(client.getServerOrigin());
        loginDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.music_server_login)
                .setMessage(R.string.music_server_origin_help)
                .setView(origin)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.music_server_login, null)
                .create();
        loginDialog.setOnShowListener(d -> loginDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        String url = client.beginLogin(origin.getText().toString());
                        try {
                            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            browser.addCategory(Intent.CATEGORY_BROWSABLE);
                            activity.startActivity(browser);
                        } catch (ActivityNotFoundException | SecurityException e) {
                            client.cancelLogin();
                            showMessage(activity.getString(R.string.music_server_browser_failed));
                            return;
                        }
                        loginDialog.dismiss();
                        update();
                    } catch (IllegalArgumentException e) {
                        origin.setError(activity.getString(R.string.music_server_invalid_origin));
                    } catch (Exception e) {
                        showMessage(activity.getString(R.string.music_server_login_failed));
                    }
                }));
        loginDialog.show();
    }

    private void sendSong() {
        if (client.isBusy() || resolvingSong) {
            return;
        }
        resolvingSong = true;
        update();
        webView.evaluateJavascript(CURRENT_SONG_SCRIPT, value -> {
            resolvingSong = false;
            if (destroyed) {
                return;
            }
            String url = value != null && value.matches("\"[A-Za-z0-9_-]{11}\"")
                    ? MusicServerUrls.songUrlForId(value.substring(1, value.length() - 1)) : null;
            if (url == null) {
                showMessage(activity.getString(R.string.music_server_no_song));
            } else {
                client.submitJob(url, this::completed);
            }
            update();
        });
    }

    private void completed(boolean success, String message) {
        if (!destroyed && !activity.isFinishing()) {
            showMessage(message);
            update();
        }
    }

    private void showMessage(String message) {
        Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}
