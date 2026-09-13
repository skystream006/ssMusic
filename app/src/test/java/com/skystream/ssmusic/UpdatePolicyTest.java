package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.junit.Test;

public class UpdatePolicyTest {
    @Test
    public void comparesNumericComponentsRatherThanStrings() {
        assertTrue(UpdatePolicy.compareVersions("v0.10.01", "0.9.99") > 0);
        assertTrue(UpdatePolicy.compareVersions("0.10.2", "v0.10.10") < 0);
        assertEquals(0, UpdatePolicy.compareVersions("v0.10.01", "0.10.1.0"));
        assertTrue(UpdatePolicy.compareVersions("1.0", "0.999.999") > 0);
    }

    @Test
    public void supportsLargeComponentsWithoutOverflow() {
        assertTrue(UpdatePolicy.compareVersions("99999999999999999999999.0", "2") > 0);
    }

    @Test
    public void rejectsNonStableAndMalformedVersions() {
        for (String version : new String[]{null, "", "v", "0.10-beta", "1.0+build",
                "1..2", " 1.0", "1.-1", "V1.0"}) {
            try {
                UpdatePolicy.compareVersions(version, "1");
                throw new AssertionError("Accepted " + version);
            } catch (IllegalArgumentException expected) {
                // Malformed release tags must not trigger an update.
            }
        }
    }

    @Test
    public void stripsOnlyVersionPrefixForDisplay() {
        assertEquals("0.10.01", UpdatePolicy.displayVersion("v0.10.01"));
        assertEquals("0.10.01", UpdatePolicy.displayVersion("0.10.01"));
    }

    @Test
    public void allowsOnlyRepositoryApkAsInitialDownload() throws Exception {
        assertTrue(allowed("https://github.com/skystream006/ssMusic/releases/download/v1/app.apk", true));
        assertFalse(allowed("https://github.com/other/ssMusic/releases/download/v1/app.apk", true));
        assertFalse(allowed("https://github.com/skystream006/ssMusic/releases/download/v1/app.zip", true));
        assertFalse(allowed("https://release-assets.githubusercontent.com/file.apk", true));
    }

    @Test
    public void permitsExpectedGithubCdnRedirects() throws Exception {
        assertTrue(allowed("https://release-assets.githubusercontent.com/path/file?token=signed", false));
        assertTrue(allowed("https://objects.githubusercontent.com/path/file", false));
        assertTrue(allowed("https://github-releases.githubusercontent.com/path/file", false));
    }

    @Test
    public void rejectsDowngradesLookalikesUserInfoAndUnexpectedPorts() throws Exception {
        for (String url : new String[]{
                "http://release-assets.githubusercontent.com/file",
                "https://release-assets.githubusercontent.com.evil.example/file",
                "https://evil.example/file.apk",
                "https://user@objects.githubusercontent.com/file",
                "https://objects.githubusercontent.com:444/file",
                "https://objects.githubusercontent.com/file#fragment",
                "https://raw.githubusercontent.com/file",
                "https://github.com/skystream006/ssMusic/releases/download/v1/../app.apk"}) {
            assertFalse(url, allowed(url, false));
        }
    }

    @Test
    public void metadataRemainsOnExactEndpoint() throws Exception {
        assertTrue(UpdatePolicy.isAllowedUrl(new URL(UpdatePolicy.LATEST_URL), true, true));
        assertFalse(UpdatePolicy.isAllowedUrl(new URL(
                "https://api.github.com/repos/other/project/releases/latest"), true, false));
        assertFalse(UpdatePolicy.isAllowedUrl(new URL(
                "https://objects.githubusercontent.com/file"), true, false));
    }

    @Test
    public void archiveMustMatchPackageReleaseAndBeStrictlyNewer() {
        assertTrue(matches("com.skystream.ssmusic", 36, "0.10.02", "v0.10.2"));
        assertFalse(matches("other.package", 36, "0.10.02", "0.10.02"));
        assertFalse(matches("com.skystream.ssmusic", 35, "0.10.02", "0.10.02"));
        assertFalse(matches("com.skystream.ssmusic", 34, "0.10.02", "0.10.02"));
        assertFalse(matches("com.skystream.ssmusic", 36, "0.10.01", "0.10.01"));
        assertFalse(matches("com.skystream.ssmusic", 36, "0.9.99", "0.9.99"));
        assertFalse(matches("com.skystream.ssmusic", 36, "0.10.03", "0.10.02"));
        assertFalse(matches("com.skystream.ssmusic", 36, null, "0.10.02"));
    }

    @Test
    public void cachedApkCanOnlyBeReusedForTheLatestRelease() {
        assertTrue(matches("com.skystream.ssmusic", 36, "0.10.02", "v0.10.2"));
        assertFalse(matches("com.skystream.ssmusic", 36, "0.10.02", "v0.10.03"));
        assertFalse(matches("com.skystream.ssmusic", 37, "0.10.03", "v0.10.02"));
    }

    @Test
    public void cachedApkIsStaleAfterItsVersionOrANewerVersionIsInstalled() {
        assertFalse(UpdatePolicy.archiveMatches("com.skystream.ssmusic", 36, "0.10.02",
                "com.skystream.ssmusic", 36, "0.10.02", "0.10.02"));
        assertFalse(UpdatePolicy.archiveMatches("com.skystream.ssmusic", 37, "0.10.03",
                "com.skystream.ssmusic", 36, "0.10.02", "0.10.02"));
    }

    private boolean matches(String name, long code, String version, String release) {
        return UpdatePolicy.archiveMatches("com.skystream.ssmusic", 35, "0.10.01",
                name, code, version, release);
    }

    private boolean allowed(String value, boolean initial) throws Exception {
        return UpdatePolicy.isAllowedUrl(new URL(value), false, initial);
    }
}
