package com.skystream.ssmusic;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Validation shared by release discovery and APK installation. */
final class UpdatePolicy {
    static final String LATEST_URL =
            "https://api.github.com/repos/skystream006/ssMusic/releases/latest";
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final String DOWNLOAD_PATH = "/skystream006/ssMusic/releases/download/";

    private UpdatePolicy() {}

    static int compareVersions(String left, String right) {
        String[] a = components(left);
        String[] b = components(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            BigInteger x = i < a.length ? new BigInteger(a[i]) : BigInteger.ZERO;
            BigInteger y = i < b.length ? new BigInteger(b[i]) : BigInteger.ZERO;
            int result = x.compareTo(y);
            if (result != 0) return result;
        }
        return 0;
    }

    private static String[] components(String version) {
        if (version == null || version.length() > 128
                || !version.matches("v?[0-9]+(?:\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Not a stable numeric version");
        }
        return (version.startsWith("v") ? version.substring(1) : version).split("\\.");
    }

    static String displayVersion(String version) {
        components(version);
        return version.startsWith("v") ? version.substring(1) : version;
    }

    static boolean isAllowedUrl(URL url, boolean metadata, boolean initial) {
        try {
            URI uri = url.toURI();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getFragment() != null || uri.getHost() == null
                    || !uri.normalize().getRawPath().equals(uri.getRawPath())) {
                return false;
            }
            if (metadata) return LATEST_URL.equals(url.toExternalForm());
            String host = uri.getHost();
            if ("github.com".equalsIgnoreCase(host)) {
                String path = uri.getRawPath();
                return path.startsWith(DOWNLOAD_PATH)
                        && path.substring(DOWNLOAD_PATH.length()).matches("[^/]+/[^/]+\\.apk")
                        && uri.getRawQuery() == null;
            }
            return !initial && ("release-assets.githubusercontent.com".equalsIgnoreCase(host)
                    || "objects.githubusercontent.com".equalsIgnoreCase(host)
                    || "github-releases.githubusercontent.com".equalsIgnoreCase(host));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    static boolean archiveMatches(String installedPackage, long installedCode,
            String installedVersion, String archivePackage, long archiveCode,
            String archiveVersion, String releaseVersion) {
        try {
            return installedPackage.equals(archivePackage)
                    && archiveCode > installedCode
                    && compareVersions(archiveVersion, installedVersion) > 0
                    && compareVersions(archiveVersion, releaseVersion) == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
