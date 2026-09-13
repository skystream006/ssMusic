package com.skystream.ssmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackKeepAliveService extends Service {

    private static final String TAG = "PlaybackService";
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final String WAKE_LOCK_TAG = "ssmusic:playback";
    private static final String ACTION_TOGGLE_PLAYBACK = "com.skystream.ssmusic.TOGGLE_PLAYBACK";
    private static final String ACTION_PLAY = "com.skystream.ssmusic.PLAY";
    private static final String ACTION_PAUSE = "com.skystream.ssmusic.PAUSE";
    private static final String ACTION_NEXT = "com.skystream.ssmusic.NEXT";
    private static final String ACTION_PREVIOUS = "com.skystream.ssmusic.PREVIOUS";
    private static final String ACTION_STOP = "com.skystream.ssmusic.STOP";
    static final String ACTION_SYNC_PLAYBACK_STATE = "com.skystream.ssmusic.SYNC_PLAYBACK_STATE";
    static final String EXTRA_SYNC_PLAYING = "sync_playing";
    static final String EXTRA_SYNC_POSITION_MS = "sync_position_ms";
    static final String EXTRA_SYNC_DURATION_MS = "sync_duration_ms";
    static final String EXTRA_SYNC_TITLE = "sync_title";
    static final String EXTRA_SYNC_ARTIST = "sync_artist";
    static final String EXTRA_SYNC_THUMBNAIL_URL = "sync_thumbnail_url";
    static final String EXTRA_SYNC_START_TOKEN = "sync_start_token";
    private static final int REQUEST_PREVIOUS = 1;
    private static final int REQUEST_TOGGLE_PLAYBACK = 2;
    private static final int REQUEST_NEXT = 3;
    private static final int REQUEST_STOP = 4;
    private static final int MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_THUMBNAIL_SIZE_PX = 512;
    private static final int THUMBNAIL_TIMEOUT_MS = 5000;
    // Safety timeout so the wake lock cannot be held forever if release() is ever missed;
    // renewed on every onStartCommand call while playback keeps the service alive.
    private static final long WAKE_LOCK_TIMEOUT_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(6);

    private PowerManager.WakeLock wakeLock;
    private MediaSession mediaSession;
    private boolean playing = true;
    private long positionMs;
    private long durationMs;
    private long startToken;
    private String title;
    private String artist;
    private String thumbnailUrl;
    private Bitmap thumbnail;
    private int thumbnailRequestVersion;
    private boolean destroyed;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.init(this);
        Logger.event(TAG, "onStartCommand, action: " + (intent == null ? null : intent.getAction()));
        destroyed = false;
        createNotificationChannel();
        try {
            activateMediaSession();
            handleAction(intent, false);
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            // The notification stays up while paused so transport buttons keep working,
            // but the wake lock is only needed while audio is actually playing. The WebView owns
            // audio output and its audio focus, so this notification service must not request it.
            if (playing) {
                acquireWakeLock();
            } else {
                releaseWakeLock();
            }
            // startForeground must run first: a start delivered by the system still requires it
            // before the service may go away.
            if (ACTION_STOP.equals(intent == null ? null : intent.getAction())) {
                stopPlayback();
            }
        } catch (RuntimeException e) {
            Logger.error(TAG, "Unable to start playback notification", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Some OEM launchers/task managers kill a started service's process once its task is
        // swiped from recents; logging this makes that scenario distinguishable from a normal
        // stop when diagnosing playback drops that coincide with the app leaving foreground.
        Logger.event(TAG, "onTaskRemoved, playing: " + playing);
        super.onTaskRemoved(rootIntent);
    }

    private void stopPlayback() {
        Logger.event(TAG, "Stopping playback notification");
        releaseWakeLock();
        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Logger.event(TAG, "onDestroy");
        destroyed = true;
        // Let the activity know the notification is gone so it stops syncing state to a dead service.
        handleMediaCommand(MainActivity.MEDIA_COMMAND_SERVICE_STOPPED, 0L, startToken);
        releaseWakeLock();
        thumbnailExecutor.shutdownNow();
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
                .setContentTitle(displayTitle())
                .setContentText(displayArtist())
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDeleteIntent(serviceIntent(ACTION_STOP, REQUEST_STOP))
                .setOngoing(playing);
        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail);
        }
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                getString(R.string.playback_previous),
                serviceIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS))
                .build());
        builder.addAction(new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? getString(R.string.playback_pause) : getString(R.string.playback_play),
                serviceIntent(ACTION_TOGGLE_PLAYBACK, REQUEST_TOGGLE_PLAYBACK))
                .build());
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                getString(R.string.playback_next),
                serviceIntent(ACTION_NEXT, REQUEST_NEXT))
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
                }

                @Override
                public void onPause() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_PAUSE);
                }

                @Override
                public void onSkipToNext() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_NEXT);
                }

                @Override
                public void onSkipToPrevious() {
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_PREVIOUS);
                }

                @Override
                public void onSeekTo(long position) {
                    positionMs = Math.max(0L, position);
                    mediaSession.setPlaybackState(playbackStateForCurrentState());
                    handleMediaCommand(MainActivity.MEDIA_COMMAND_SEEK, position);
                }
            });
        }
        updateMediaMetadata();
        mediaSession.setPlaybackState(playbackStateForCurrentState());
        mediaSession.setActive(true);
    }

    private void handleAction(Intent intent, boolean notify) {
        String action = intent == null ? null : intent.getAction();
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
        } else if (ACTION_STOP.equals(action)) {
            handleMediaCommand(MainActivity.MEDIA_COMMAND_STOP);
            setPlaying(false, notify);
        } else if (ACTION_SYNC_PLAYBACK_STATE.equals(action) && intent != null) {
            startToken = Math.max(startToken, intent.getLongExtra(EXTRA_SYNC_START_TOKEN, startToken));
            positionMs = Math.max(0L, intent.getLongExtra(EXTRA_SYNC_POSITION_MS, positionMs));
            durationMs = Math.max(0L, intent.getLongExtra(EXTRA_SYNC_DURATION_MS, durationMs));
            if (intent.hasExtra(EXTRA_SYNC_TITLE)) {
                title = intent.getStringExtra(EXTRA_SYNC_TITLE);
            }
            if (intent.hasExtra(EXTRA_SYNC_ARTIST)) {
                artist = intent.getStringExtra(EXTRA_SYNC_ARTIST);
            }
            if (intent.hasExtra(EXTRA_SYNC_THUMBNAIL_URL)) {
                setThumbnailUrl(intent.getStringExtra(EXTRA_SYNC_THUMBNAIL_URL));
            }
            updateMediaMetadata();
            setPlaying(intent.hasExtra(EXTRA_SYNC_PLAYING)
                    ? intent.getBooleanExtra(EXTRA_SYNC_PLAYING, playing) : playing, notify);
        }
    }

    private void setPlaying(boolean value, boolean notify) {
        if (playing != value) {
            Logger.event(TAG, "Notification playing state changed to " + value);
        }
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
        handleMediaCommand(command, 0L);
    }

    private void handleMediaCommand(int command, long positionMs) {
        handleMediaCommand(command, positionMs, startToken);
    }

    private void handleMediaCommand(int command, long positionMs, long token) {
        Logger.event(TAG, "Sending media command " + command + ", position: " + positionMs);
        Intent intent = new Intent(MainActivity.ACTION_MEDIA_COMMAND);
        intent.setPackage(getPackageName());
        intent.putExtra(MainActivity.EXTRA_MEDIA_COMMAND, command);
        intent.putExtra(MainActivity.EXTRA_MEDIA_START_TOKEN, token);
        if (command == MainActivity.MEDIA_COMMAND_SEEK) {
            intent.putExtra(MainActivity.EXTRA_MEDIA_POSITION_MS, positionMs);
        }
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
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        positionMs,
                        playing ? 1f : 0f,
                        android.os.SystemClock.elapsedRealtime())
                .build();
    }

    private void updateMediaMetadata() {
        if (mediaSession == null) {
            return;
        }
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE,
                        displayTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, displayArtist());
        if (durationMs > 0L) {
            metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        }
        if (thumbnail != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, thumbnail);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, thumbnail);
        }
        mediaSession.setMetadata(metadata.build());
    }

    private void setThumbnailUrl(String value) {
        String sanitized = sanitizeThumbnailUrl(value);
        if (stringEquals(thumbnailUrl, sanitized)) {
            return;
        }
        thumbnailUrl = sanitized;
        thumbnail = null;
        int requestVersion = ++thumbnailRequestVersion;
        if (thumbnailUrl != null) {
            loadThumbnailAsync(thumbnailUrl, requestVersion);
        }
    }

    private String sanitizeThumbnailUrl(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            URL url = new URL(normalized);
            return isAllowedThumbnailUrl(url)
                    ? normalized : null;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isAllowedThumbnailUrl(URL url) {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null) {
            return false;
        }
        String host = url.getHost().toLowerCase(Locale.US);
        return host.equals("music.youtube.com")
                || host.endsWith(".youtube.com")
                || host.endsWith(".ytimg.com")
                || host.endsWith(".ggpht.com")
                || host.endsWith(".googleusercontent.com")
                || host.endsWith(".gstatic.com");
    }

    private void loadThumbnailAsync(String url, int requestVersion) {
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = downloadThumbnail(url);
            mainHandler.post(() -> {
                if (destroyed || requestVersion != thumbnailRequestVersion
                        || !stringEquals(thumbnailUrl, url)) {
                    return;
                }
                thumbnail = bitmap;
                updateMediaMetadata();
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, buildNotification());
                }
            });
        });
    }

    private Bitmap downloadThumbnail(String source) {
        HttpURLConnection connection = null;
        try {
            URL requestedUrl = new URL(source);
            if (!isAllowedThumbnailUrl(requestedUrl)) {
                return null;
            }
            connection = (HttpURLConnection) requestedUrl.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(THUMBNAIL_TIMEOUT_MS);
            connection.setReadTimeout(THUMBNAIL_TIMEOUT_MS);
            int responseCode = connection.getResponseCode();
            if (!isAllowedThumbnailUrl(connection.getURL())) {
                return null;
            }
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                return null;
            }
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_THUMBNAIL_BYTES) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = readThumbnailBytes(input);
                if (bytes == null) {
                    return null;
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = thumbnailSampleSize(bounds);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
            }
        } catch (IOException | RuntimeException e) {
            Logger.debug(TAG, "Unable to load media thumbnail: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readThumbnailBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_THUMBNAIL_BYTES) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private int thumbnailSampleSize(BitmapFactory.Options bounds) {
        int sampleSize = 1;
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        while (width / sampleSize > MAX_THUMBNAIL_SIZE_PX
                || height / sampleSize > MAX_THUMBNAIL_SIZE_PX) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private boolean stringEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private String displayTitle() {
        return title == null || title.isEmpty()
                ? getString(R.string.playback_notification_title) : title;
    }

    private String displayArtist() {
        return artist == null || artist.isEmpty()
                ? getString(R.string.playback_notification_unknown_artist) : artist;
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
