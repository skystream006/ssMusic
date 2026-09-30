package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural checks for Android UI wiring, which is unavailable in local JVM tests. */
public class AppUpdaterProgressTest {
    private String source(String name) throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssmusic/" + name + ".java")), StandardCharsets.UTF_8);
    }

    @Test
    public void reopeningSettingsBindsCurrentProgressAndDismissalReleasesOnlyItsView() throws Exception {
        assertTrue(source("MainActivity").contains("appUpdater.bindProgress(header)"));
        String updater = source("AppUpdater");
        String bind = updater.substring(updater.indexOf("public void bindProgress("),
                updater.indexOf("private void downloadProgress("));
        assertTrue(bind.contains("progressHeader = header"));
        assertTrue(bind.contains("renderProgress()"));
        assertTrue(bind.contains("onViewDetachedFromWindow"));
        assertTrue(bind.contains("if (progressHeader == view) progressHeader = null"));
        assertTrue(bind.contains("view.removeOnAttachStateChangeListener(this)"));
        assertFalse(bind.contains("client.cancel()"));
        assertFalse(bind.contains("setOnDismissListener"));
    }

    @Test
    public void progressIsPostedToUiThreadAndResetForEachDownload() throws Exception {
        String updater = source("AppUpdater");
        String start = updater.substring(updater.indexOf("private void checked("),
                updater.indexOf("private static JSONObject selectApk("));
        assertTrue(start.indexOf("if (!manualRequested) return") < start.indexOf("downloading = true"));
        assertTrue(start.contains("downloadedBytes = 0"));
        assertTrue(start.contains("downloadSize = 0"));
        assertTrue(start.indexOf("renderProgress()") < start.indexOf("executor.execute("));
        assertTrue(start.contains("if (percent != lastPercent[0])"));
        assertTrue(start.contains("main.post(() -> downloadProgress(bytes, total))"));
        String progress = updater.substring(updater.indexOf("private void downloadProgress("),
                updater.indexOf("private void checked("));
        assertTrue(progress.contains("if (destroyed || !downloading) return"));
        assertTrue(progress.contains("if (destroyed || !resumed || progressHeader == null) return"));
        assertTrue(progress.contains("setVisibility(downloading ? View.VISIBLE : View.GONE)"));
        assertTrue(progress.contains("setEnabled(!downloading)"));
        assertTrue(progress.contains("bar.setIndeterminate(downloadSize <= 0)"));
        assertTrue(progress.contains("bar.setProgress((int) (downloadedBytes * 100 / downloadSize))"));
        assertTrue(progress.contains("downloadedBytes / 1024, downloadSize / 1024"));
    }

    @Test
    public void successAndFailureHideProgressBeforeInstallationOrError() throws Exception {
        String updater = source("AppUpdater");
        String success = updater.substring(updater.indexOf("private void downloaded("),
                updater.indexOf("private File storedApk("));
        assertTrue(success.indexOf("downloading = false") < success.indexOf("renderProgress()"));
        assertTrue(success.indexOf("renderProgress()") < success.indexOf("maybeInstall()"));
        String failure = updater.substring(updater.indexOf("private void failed("),
                updater.indexOf("private PackageInfo installedPackage("));
        assertTrue(failure.indexOf("downloading = false") < failure.indexOf("renderProgress()"));
        assertTrue(failure.indexOf("renderProgress()") < failure.indexOf("message("));
    }

    @Test
    public void resumeRefreshesProgressAndDestructionReleasesViewsAndCancelsDownload() throws Exception {
        String updater = source("AppUpdater");
        String resume = updater.substring(updater.indexOf("public void onResume("),
                updater.indexOf("public void onPause("));
        assertTrue(resume.indexOf("resumed = true") < resume.indexOf("renderProgress()"));
        String destroy = updater.substring(updater.indexOf("public void destroy("),
                updater.indexOf("public void bindProgress("));
        assertTrue(destroy.contains("progressHeader = null"));
        assertTrue(destroy.contains("client.cancel()"));
        assertTrue(destroy.contains("executor.shutdownNow()"));
    }
}
