# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode
- system, light, or dark app theme
- back, forward, refresh, and home navigation

The app requests the browser permissions YouTube Music may need, including camera, microphone, notification, and foreground playback permissions. When you send the app to the background, it keeps a low-priority playback notification active so music can continue playing.

## Build

```sh
./gradlew test assembleDebug
```

GitHub Actions builds APK artifacts with the same version-bump.
