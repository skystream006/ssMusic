# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode
- system, light, or dark app theme
- video display default for showing the song thumbnail instead of available video
- back, forward, refresh, and home navigation
- optional debug logging
- a **Stats for nerds** overlay with live memory, network, and storage usage

The YouTube Music wordmark in the page is replaced with the bundled ssMusic logo, which is served
to the WebView from a synthetic same-origin path instead of the network.

In the expanded media view, swipe down on the video area to minimize the player, left for
the previous song, or right for the next song. These gestures also work while the song
thumbnail is shown. Taps and player controls retain their normal behavior.

## Stats for nerds

Enable **Stats for nerds** under **Diagnostics** in settings. The preference is saved, and
the touch-through overlay stays above the app content but below settings, so playback and
navigation remain usable. Settings can be scrolled on smaller screens.

Memory shows the current app process's proportional set size (PSS), excluding isolated
WebView renderer processes. Network shows download/upload rates for the app UID as reported
by Android, which may exclude traffic attributed to isolated WebView processes. The first
network sample and unsupported metrics display **Unavailable**, not a misleading zero.
Memory and network refresh approximately every second while the app is visible.
Storage counts app-private data and cache (including WebView storage), plus app-specific
external files/cache, not the installed APK or device-wide usage. It refreshes approximately
every 30 seconds on a worker thread. Sampling stops when disabled or the app is hidden.

## Logging

Logging is off by default. Turn on **Enable logging** in the settings panel to record app
activity — lifecycle events, navigation, permission decisions, blocked ad requests, playback
state, media notification commands, media-player swipes (direction, action, and whether the
control was clicked or unavailable), and uncaught exceptions. Every entry names the calling
code, and warnings, errors, and crashes carry a full stack trace. Entries go to logcat and to
a private log file (`files/logs/ssmusic.log`) that is rotated once it reaches 512 KB. Use
**Share log** to send the file to another app and **Clear log** to delete it. Because the log
records visited YouTube Music URLs and track metadata, only share it with people you trust.

The app requests the browser permissions YouTube Music may need, including camera, microphone, notification, and foreground playback permissions. When you send the app to the background, it keeps a low-priority playback notification active so music can continue playing.

## Build

```sh
./gradlew test assembleDebug
```

GitHub Actions builds APK artifacts with the same version-bump.
