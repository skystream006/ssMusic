package com.skystream.ssmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class PlaybackKeepAliveService extends Service {

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final String WAKE_LOCK_TAG = "ssmusic:playback";
    private static final String ACTION_TOGGLE_PLAYBACK = "com.skystream.ssmusic.TOGGLE_PLAYBACK";
    private static final String ACTION_PLAY = "com.skystream.ssmusic.PLAY";
    private static final String ACTION_PAUSE = "com.skystream.ssmusic.PAUSE";
    private static final String ACTION_NEXT = "com.skystream.ssmusic.NEXT";
    private static final String ACTION_PREVIOUS = "com.skystream.ssmusic.PREVIOUS";
    // Safety timeout so the wake lock cannot be held forever if release() is ever missed;
    // renewed on every onStartCommand call while playback keeps the service alive.
    private static final long WAKE_LOCK_TIMEOUT_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(6);

    private PowerManager.WakeLock wakeLock;
    private MediaSession mediaSession;
    private boolean playing = true;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        try {
            activateMediaSession();
            handleAction(intent == null ? null : intent.getAction(), false);
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            acquireWakeLock();
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            wakeLock.setReferenceCounted(false);
        }
        // acquire(timeout) renews the timeout even if already held, so repeated
        // onStartCommand calls keep the lock alive for as long as playback continues.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingIntentFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.playback_notification_title))
                .setContentText(getString(R.string.playback_notification_text))
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                getString(R.string.playback_previous),
                serviceIntent(ACTION_PREVIOUS, 1))
                .build());
        builder.addAction(new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? getString(R.string.playback_pause) : getString(R.string.playback_play),
                serviceIntent(ACTION_TOGGLE_PLAYBACK, 2))
                .build());
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                getString(R.string.playback_next),
                serviceIntent(ACTION_NEXT, 3))
                .build());
        if (mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private void activateMediaSession() {
        if (mediaSession == null) {
            mediaSession = new MediaSession(this, "ssMusicPlayback");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_PLAY);
                    setPlaying(true, true);
                }

                @Override
                public void onPause() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_PAUSE);
                    setPlaying(false, true);
                }

                @Override
                public void onSkipToNext() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_NEXT);
                }

                @Override
                public void onSkipToPrevious() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_PREVIOUS);
                }
            });
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE,
                            getString(R.string.playback_notification_title))
                    .build());
        }
        mediaSession.setPlaybackState(playbackStateForCurrentState());
        mediaSession.setActive(true);
    }

    private void handleAction(String action, boolean notify) {
        if (ACTION_PLAY.equals(action)) {
            handleMediaCommand(MainActivity.MEDIA_COMMAND_PLAY);
            setPlaying(true, notify);
        } else if (ACTION_PAUSE.equals(action)) {
            handleMediaCommand(MainActivity.MEDIA_COMMAND_PAUSE);
            setPlaying(false, notify);
        } else if (ACTION_NEXT.equals(action)) {
            handleMediaCommand(MainActivity.MEDIA_COMMAND_NEXT);
        } else if (ACTION_PREVIOUS.equals(action)) {
            handleMediaCommand(MainActivity.MEDIA_COMMAND_PREVIOUS);
        } else if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
            int command = playing ? MainActivity.MEDIA_COMMAND_PAUSE : MainActivity.MEDIA_COMMAND_PLAY;
            handleMediaCommand(command);
            setPlaying(!playing, notify);
        }
    }

    private void setPlaying(boolean value, boolean notify) {
        playing = value;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(playbackStateForCurrentState());
        }
        if (notify) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification());
            }
        }
    }

    private void handleMediaCommand(int command) {
        Intent intent = new Intent(MainActivity.ACTION_MEDIA_COMMAND);
        intent.setPackage(getPackageName());
        intent.putExtra(MainActivity.EXTRA_MEDIA_COMMAND, command);
        sendBroadcast(intent);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackKeepAliveService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private PlaybackState playbackStateForCurrentState() {
        return new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        playing ? 1f : 0f)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
