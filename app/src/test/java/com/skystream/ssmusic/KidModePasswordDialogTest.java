package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** Structural checks for the native dialog, which is unavailable in local JVM tests. */
public class KidModePasswordDialogTest {
    private String activity() throws IOException {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/skystream/ssmusic/MainActivity.java")), StandardCharsets.UTF_8);
    }

    @Test
    public void homeOverlayFollowsKidModeAtStartupAndAfterPasswordChanges() throws IOException {
        String source = activity();
        String startup = source.substring(source.indexOf("protected void onCreate("),
                source.indexOf("protected void onStart("));
        assertTrue(startup.contains("kidModeHomeButton = findViewById(R.id.kid_mode_home_button)"));
        assertTrue(startup.contains(
                "kidModeHomeButton.setOnClickListener(v -> loadUrl(KidModeNavigation.LIBRARY_URL))"));
        assertTrue(startup.indexOf("kidModeHomeButton =") < startup.indexOf("configureWebView()"));
        String configuration = source.substring(source.indexOf("private void configureWebView("),
                source.indexOf("private boolean isKidModeEnabled("));
        assertTrue(configuration.contains("updateKidModeScript()"));
        String update = source.substring(source.indexOf("private void updateKidModeScript("),
                source.indexOf("private String urlFromIntent("));
        assertTrue(update.contains(
                "kidModeHomeButton.setVisibility(isKidModeEnabled() ? View.VISIBLE : View.GONE)"));
        String completion = source.substring(source.indexOf("String result = verifier;"),
                source.indexOf("private void setKidModePasswordBusy"));
        assertTrue(completion.indexOf("updateKidModeScript()") > completion.indexOf("if (!editor.commit())"));
    }

    @Test
    public void validSubmissionShowsLoadingBeforePasswordWork() throws IOException {
        String source = activity();
        String submission = source.substring(source.indexOf("private void showKidModePasswordDialog"),
                source.indexOf("private void setKidModePasswordBusy"));
        int busy = submission.indexOf("setKidModePasswordBusy(dialog, fields, loading, enabling, true)");
        assertTrue(busy > submission.indexOf("R.string.kid_mode_empty_password"));
        assertTrue(busy > submission.indexOf("R.string.kid_mode_password_mismatch"));
        assertTrue(busy < submission.indexOf("passwordExecutor.execute("));
        assertTrue(submission.contains("keyboard.hideSoftInputFromWindow(password.getWindowToken(), 0)"));
        assertTrue(submission.contains("loading.setIndeterminate(true)"));
        assertTrue(submission.contains("Arrays.fill(secret, '\\0')"));
    }

    @Test
    public void busyStateHidesFormAndPreventsDismissalOrResubmission() throws IOException {
        String source = activity();
        String busyState = source.substring(source.indexOf("private void setKidModePasswordBusy"),
                source.indexOf("private EditText passwordField"));
        assertTrue(busyState.contains("fields.setVisibility(busy ? View.GONE : View.VISIBLE)"));
        assertTrue(busyState.contains("loading.setVisibility(busy ? View.VISIBLE : View.GONE)"));
        assertTrue(busyState.contains("R.string.kid_mode_enabling : R.string.kid_mode_unlocking"));
        for (String button : new String[]{"BUTTON_POSITIVE", "BUTTON_NEGATIVE"}) {
            assertTrue(busyState.contains("dialog.getButton(AlertDialog." + button + ").setEnabled(!busy)"));
            assertTrue(busyState.contains("dialog.getButton(AlertDialog." + button
                    + ").setVisibility(busy ? View.GONE : View.VISIBLE)"));
        }
        assertTrue(busyState.contains("dialog.setCancelable(!busy)"));
        assertTrue(busyState.contains("dialog.setCanceledOnTouchOutside(!busy)"));
    }

    @Test
    public void formReturnsOnlyOnFailureAndSuccessDismissesSameDialog() throws IOException {
        String source = activity();
        String completion = source.substring(source.indexOf("String result = verifier;"),
                source.indexOf("private void setKidModePasswordBusy"));
        String restore = "setKidModePasswordBusy(dialog, fields, loading, enabling, false)";
        int failure = completion.indexOf("if (failure != 0)");
        int commit = completion.indexOf("if (!editor.commit())");
        int success = completion.indexOf("updateKidModeScript()");
        assertFalse(completion.substring(0, failure).contains(restore));
        assertTrue(completion.substring(failure, commit).contains(restore));
        assertTrue(completion.substring(commit, success).contains(restore));
        assertFalse(completion.substring(success).contains(restore));
        assertTrue(completion.substring(success).contains("dialog.dismiss()"));
        assertTrue(completion.contains("isFinishing() || isDestroyed() || !dialog.isShowing()"));
        assertFalse(completion.contains("showKidModePasswordDialog("));
    }
}
