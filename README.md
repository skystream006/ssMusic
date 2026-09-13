# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode
- system, light, or dark app theme
- back, forward, refresh, and home navigation
- optional debug logging

The YouTube Music wordmark in the page is replaced with the bundled ssMusic logo, which is served
to the WebView from a synthetic same-origin path instead of the network.

## Logging

Logging is off by default. Turn on **Enable logging** in the settings panel to record app
activity — lifecycle events, navigation, permission decisions, blocked ad requests, playback
state, media notification commands, and uncaught exceptions. Every entry names the calling
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
