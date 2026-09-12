# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode
- system, light, or dark app theme
- back, forward, refresh, and home navigation

The app requests the browser permissions YouTube Music may need, including camera, microphone, and Android notification permission.

## Build

```sh
./gradlew test assembleDebug
```

GitHub Actions builds APK artifacts with the same version-bump and upload workflow used by ssYoutube. The debug build uses the checked-in debug keystore so APK artifacts from later workflow runs can update earlier installs with the same package name.
