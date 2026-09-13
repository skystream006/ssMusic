# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode
- system, light, or dark app theme
- video display default for showing the song thumbnail instead of available video
- an **Open supported links** shortcut to Android's settings for handling `music.youtube.com` links
- **Check for updates** using this repository's latest GitHub release
- back, forward, refresh, and home navigation
- optional debug logging
- a **Stats for nerds** overlay with live memory, network, and storage usage

The YouTube Music wordmark in the page is replaced with the bundled ssMusic logo, which is served
to the WebView from a synthetic same-origin path instead of the network.

In the expanded media view, swipe down on the video area to minimize the player, up to
open **Up next**, left for the previous song, or right for the next song. These gestures also work while the song
thumbnail is shown. Taps and player controls retain their normal behavior.

## Supported links

To open YouTube Music links in ssMusic, choose **Open supported links** in the settings
panel, enable the Android setting, and select `music.youtube.com` if prompted. Android 12+
opens the app's link settings directly; older devices (or devices without that screen)
open App info, where **Open by default** can be configured. Android requires user approval;
the app cannot silently make itself the default link handler.

## App updates

On a fresh app launch, ssMusic checks GitHub for a newer stable release and shows
**Update available to version {version}** when one exists. This automatic check does
not download or install anything.

Choose **Check for updates** in settings to check manually. If already current, a toast
shows **App is up to date with latest version {version}**. If behind, the app downloads
the release APK into its private storage and opens Android's installer. On Android 8+,
allow installation from ssMusic if prompted, then return to the app to continue.
Android requires your confirmation and a compatible signing key; updates are never
installed silently. Checking and downloading require an internet connection.

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
**View log** to open a scrollable, selectable text snapshot in the app, with **Refresh** to
load newer entries. This also works with logging disabled for entries already saved. Use
**Share log** to send the file to another app and **Clear log** to delete it. Because the log
records visited YouTube Music URLs and track metadata, only share it with people you trust.

To diagnose swipes, enable logging, reproduce the gesture, then open **View log**. Native
touch reception and gesture rejection/cancellation are logged as well as completed actions,
so a gesture that never reaches the page's touch handlers is no longer silent.

The app requests the browser permissions YouTube Music may need, including camera, microphone, notification, and foreground playback permissions. When you send the app to the background, it keeps a low-priority playback notification active so music can continue playing.

## Build

```sh
./gradlew test assembleDebug
```

GitHub Actions tests and builds debug APK artifacts using the version committed in
`app/build.gradle`. Bump both `versionName` and `versionCode` there for each new release.
Successful builds on `main` publish a GitHub release tagged `v<versionName>` with the
debug-signed APK and generated release notes. Existing releases are left unchanged;
pull request builds only upload artifacts.
