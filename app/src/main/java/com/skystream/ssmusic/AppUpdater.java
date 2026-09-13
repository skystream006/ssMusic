package com.skystream.ssmusic;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity-owned updater; all public entry points are called on the UI thread. */
public final class AppUpdater {
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UpdateClient client = new UpdateClient();
    private final SharedPreferences preferences;
    private final File directory;
    private volatile boolean destroyed;
    private boolean resumed;
    private boolean checking;
    private boolean downloading;
    private boolean manualRequested;
    private boolean installRequested;
    private boolean awaitingPermission;
    private boolean permissionScreenLeft;
    private File pendingApk;
    private String pendingVersion;
    private int deferredMessage;
    private String deferredVersion;

    public AppUpdater(Activity activity, boolean restoringActivity) {
        this.activity = activity;
        directory = new File(activity.getFilesDir(), "updates");
        preferences = activity.getSharedPreferences("app_updates", Activity.MODE_PRIVATE);
        if (preferences.getBoolean("pending", false)) {
            pendingApk = storedApk();
            pendingVersion = preferences.getString("version", "");
            installRequested = UpdatePolicy.shouldResumeInstallation(restoringActivity,
                    pendingApk != null && pendingApk.isFile(),
                    preferences.getBoolean("auto_install", false));
            // A recreated activity returning from Settings must not reopen Settings on denial.
            awaitingPermission = installRequested
                    && preferences.getBoolean("permission_requested", false);
            permissionScreenLeft = awaitingPermission;
        }
        if (!restoringActivity) {
            // A fresh launch may offer a cached update, but must not launch its installer.
            preferences.edit().putBoolean("auto_install", false)
                    .putBoolean("permission_requested", false).apply();
        }
    }

    public void checkForUpdates(boolean manual) {
        if (destroyed) return;
        if (checking || downloading) {
            if (manual && checking && !manualRequested) {
                manualRequested = true;
                message(R.string.update_checking, null);
            } else if (manual) {
                message(R.string.update_busy, null);
            }
            return;
        }
        if (pendingApk != null) {
            try {
                verifyArchive(pendingApk, pendingVersion);
            } catch (Exception e) {
                discardPending();
            }
        }
        if (manual) {
            installRequested = false;
            preferences.edit().putBoolean("auto_install", false).apply();
        }
        checking = true;
        manualRequested = manual;
        if (manual) message(R.string.update_checking, null);
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject(client.latestRelease());
                if (json.getBoolean("draft") || json.getBoolean("prerelease")) {
                    throw new IOException("Not a stable release");
                }
                String version = UpdatePolicy.displayVersion(json.getString("tag_name"));
                PackageInfo installed = installedPackage();
                int comparison = UpdatePolicy.compareVersions(installed.versionName, version);
                // Equal/newer installations need no APK asset, and never download one.
                main.post(() -> checked(version, comparison, json));
            } catch (Exception e) {
                main.post(this::failed);
            }
        });
    }

    public void onResume() {
        if (destroyed) return;
        resumed = true;
        if (deferredMessage != 0) {
            message(deferredMessage, deferredVersion);
            deferredMessage = 0;
            deferredVersion = null;
        }
        maybeInstall();
    }

    public void onPause() {
        resumed = false;
        if (awaitingPermission) permissionScreenLeft = true;
    }

    public void destroy() {
        destroyed = true;
        resumed = false;
        client.cancel();
        executor.shutdownNow();
    }

    private void checked(String version, int comparison, JSONObject release) {
        if (destroyed) return;
        checking = false;
        if (comparison >= 0) {
            discardPending();
            if (manualRequested) {
                message(comparison == 0 ? R.string.update_up_to_date
                        : R.string.update_newer_installed, comparison == 0 ? version : null);
            }
            return;
        }
        message(R.string.update_available, version);
        if (!manualRequested) return;
        if (pendingApk != null) {
            try {
                verifyArchive(pendingApk, version);
                pendingVersion = version;
                installRequested = true;
                preferences.edit().putString("version", version).putBoolean("pending", true)
                        .putBoolean("auto_install", true).apply();
                maybeInstall();
                return;
            } catch (Exception e) {
                discardPending();
            }
        }
        downloading = true;
        message(R.string.update_downloading, null);
        executor.execute(() -> {
            File downloaded = null;
            try {
                JSONObject asset = selectApk(release.getJSONArray("assets"));
                downloaded = client.download(asset.getString("browser_download_url"),
                        asset.getLong("size"), directory);
                verifyArchive(downloaded, version);
                File result = downloaded;
                main.post(() -> downloaded(result, version));
            } catch (Exception e) {
                if (downloaded != null) downloaded.delete();
                main.post(this::failed);
            }
        });
    }

    private static JSONObject selectApk(JSONArray assets) throws Exception {
        JSONObject selected = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!asset.getString("name").endsWith(".apk")) continue;
            if (selected != null) throw new IOException("Ambiguous APK assets");
            selected = asset;
        }
        if (selected == null) throw new IOException("No APK asset");
        return selected;
    }

    private void downloaded(File apk, String version) {
        if (destroyed) {
            apk.delete();
            return;
        }
        downloading = false;
        File old = storedApk();
        preferences.edit().putString("file", apk.getName()).putString("version", version)
                .putBoolean("pending", true).putBoolean("auto_install", true)
                .putBoolean("permission_requested", false).apply();
        if (old != null && !old.equals(apk)) old.delete();
        pendingApk = apk;
        pendingVersion = version;
        installRequested = true;
        maybeInstall();
    }

    private File storedApk() {
        String name = preferences.getString("file", "");
        return name.matches("[0-9a-fA-F-]{36}\\.apk") ? new File(directory, name) : null;
    }

    private void discardPending() {
        if (pendingApk != null) pendingApk.delete();
        pendingApk = null;
        pendingVersion = null;
        installRequested = false;
        awaitingPermission = false;
        permissionScreenLeft = false;
        // Keep the APK retryable across recreation if the installer is cancelled.
        preferences.edit().putBoolean("pending", true)
                .putBoolean("permission_requested", false)
                .putBoolean("auto_install", false).apply();
    }

    private void failed() {
        if (destroyed) return;
        checking = false;
        downloading = false;
        if (manualRequested) message(R.string.update_failed, null);
    }

    private PackageInfo installedPackage() throws PackageManager.NameNotFoundException {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    private void verifyArchive(File apk, String releaseVersion) throws Exception {
        PackageInfo archive = activity.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), 0);
        PackageInfo installed = installedPackage();
        if (archive == null || !UpdatePolicy.archiveMatches(installed.packageName,
                versionCode(installed), installed.versionName, archive.packageName,
                versionCode(archive), archive.versionName, releaseVersion)) {
            throw new IOException("APK does not match this update");
        }
        // Android's package installer enforces signing-certificate continuity.
    }

    private void maybeInstall() {
        if (destroyed || !resumed || !installRequested || pendingApk == null
                || activity.isFinishing()) return;
        if (awaitingPermission && !permissionScreenLeft) return;
        try {
            verifyArchive(pendingApk, pendingVersion);
        } catch (Exception e) {
            // The app may have been updated while Settings or the installer was open.
            discardPending();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                if (awaitingPermission) {
                    awaitingPermission = false;
                    installRequested = false;
                    preferences.edit().putBoolean("permission_requested", false)
                            .putBoolean("auto_install", false).apply();
                    message(R.string.update_install_denied, null);
                    return;
                }
                message(R.string.update_install_permission, null);
                awaitingPermission = true;
                permissionScreenLeft = false;
                preferences.edit().putBoolean("permission_requested", true)
                        .putBoolean("auto_install", true).apply();
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            }
            awaitingPermission = false;
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".updates", pendingApk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("ssMusic update", uri));
            activity.startActivity(intent);
            installRequested = false;
            preferences.edit().putBoolean("pending", false)
                    .putBoolean("permission_requested", false)
                    .putBoolean("auto_install", false).apply();
        } catch (Exception e) {
            awaitingPermission = false;
            installRequested = false;
            preferences.edit().putBoolean("permission_requested", false)
                    .putBoolean("auto_install", false).apply();
            message(R.string.update_install_failed, null);
        }
    }

    private void message(int resource, String version) {
        if (destroyed) return;
        if (!resumed) {
            deferredMessage = resource;
            deferredVersion = version;
            return;
        }
        String text = version == null ? activity.getString(resource)
                : activity.getString(resource, version);
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
