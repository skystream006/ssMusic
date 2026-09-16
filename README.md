# ssMusic

ssMusic is a Chromium WebView-based Android app dedicated to YouTube Music. It opens directly to `https://music.youtube.com/` with no address bar, keeps normal WebView cookies/history, blocks common ad and tracking requests, and provides an in-app settings panel for:

- mobile or desktop user agent mode dropdown
- system, light, or dark app theme dropdown
- video display default for showing the song thumbnail instead of available video
- an **Open supported links** shortcut to Android's settings for handling `music.youtube.com` links
- **Check for updates** using this repository's latest GitHub release
- back, forward, refresh, and home navigation
- optional debug logging
- a **Stats for nerds** overlay with live memory, network, and storage usage
- password-protected **Kid mode** for playlist-only listening

Kid mode, supported links, logging, and Stats for nerds are grouped under **Advanced**, which starts
collapsed whenever settings is opened. Tap **Advanced** to expand or collapse those controls.
**View log**, **Share log**, and **Clear log** appear together in one row.

The YouTube Music wordmark in the page is replaced with the bundled ssMusic logo, which is served
to the WebView from a synthetic same-origin path instead of the network.

In the expanded media view, swipe down on the video area to minimize the player, up to
open **Up next**, left for the previous song, or right for the next song. These gestures also work while the song
thumbnail is shown. Taps and player controls retain their normal behavior.

## Supported links

To open YouTube Music links in ssMusic, expand **Advanced** and choose **Open supported links** in the settings
panel, enable the Android setting, and select `music.youtube.com` if prompted. Android 12+
opens the app's link settings directly; older devices (or devices without that screen)
open App info, where **Open by default** can be configured. Android requires user approval;
the app cannot silently make itself the default link handler.

## Kid mode

Sign in first, then open **Settings → Advanced → Kid mode**. Enabling immediately asks
you to choose and confirm a non-empty password. Cancelling leaves the mode unchanged.
Turning it off requires that same password; an incorrect password keeps it locked.
Only a salted, slow password verifier is stored in app-private preferences, not the password.
The setting survives restarts. Keep the password safe: there is no in-app password recovery.

While enabled, launches, incoming links, and Home open
`https://music.youtube.com/library`. A Home button overlays the upper-left corner
only in Kid mode, letting you return to Library without opening Settings.
The page logo remains visible but is disabled
instead of redirecting. Open a playlist in the library before playing its songs.
The page's settings/menu buttons, navigation buttons, mini guide, search box, and Related
tab are removed. Autoplay is switched off and its section hidden and disabled.
Songs must be verified against the opened playlist, including when using notification
controls or skipping tracks; radio mixes and standalone song links are not allowed.

Kid mode requires a current Android System WebView with document-start script support.
It is an app-level restriction, not a device parental-control or explicit-content filter:
it does not rate songs, protect other apps, or prevent someone from clearing ssMusic's
Android app data. Restrictions depend on YouTube Music's page structure; if playlist
membership cannot be verified, playback is blocked rather than allowing an unknown song.

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

Enable **Stats for nerds** under **Advanced → Diagnostics** in settings. The preference is saved, and
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

Logging is off by default. Expand **Advanced** and turn on **Enable logging** in the settings panel to record app
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

Buffering keeps the playback service active, and pausing retains notification controls so you can
resume without reopening the app. Audio focus remains managed by WebView: calls and other audio
apps can still interrupt playback. Force-stopping the app or device-specific battery restrictions
can stop playback and remove its notification.

## Build

```sh
./gradlew test assembleDebug
```

Builds require Git and a full-history checkout (`git fetch --unshallow` for an existing
shallow clone). `app/build.gradle` derives both Android version fields from the number
of first-parent commits after the fixed baseline commit `23d6b35` (version `0.10.02`,
code `36`). Each new commit on `main`, including a merged pull request, advances both
fields by one: the next version is `0.10.03`, code `37`. Patch and minor components
roll over at 100 (for example, `0.10.99` becomes `0.11.00`). Do not move the baseline
or manually bump the version fields.

GitHub Actions tests and builds debug APK artifacts and reads the version from the
built APK metadata, so the app, artifact name, and release tag agree.
The **Android APK** workflow runs on pushes to `main`, pull requests, and manual
dispatches. It only tests, builds, and uploads APK artifacts; it does not publish
GitHub releases automatically after merges.

To build a release manually, open **Actions → Manual Android Release → Run workflow**.
This separate, manual-only workflow always checks out the latest `main` and uses its
Git-derived version without incrementing it again. Rebuilding the same commit uses
the same version in either workflow. It runs tests,
uploads a debug-signed APK artifact, and publishes a new release tagged
`v<versionName>` with generated release notes, marked as latest.
If that version's release already exists, it is left unchanged; the rebuilt APK is
still available in the workflow artifacts. Merge new changes into `main` when you
need a new release. Pull request artifact versions are previews, not reserved release
versions; the final version is calculated from the merged commit on `main`.
