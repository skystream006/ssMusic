package com.skystream.ssmusic;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Defines which URLs stay inside the app. YouTube Music plus the Google sign-in domains are
 * allowed so that signing in works; everything else is blocked.
 */
public final class SiteScope {

    private static final List<String> ALLOWED_HOSTS = Arrays.asList(
            "music.youtube.com",
            "youtube.com",
            "ytimg.com",
            "ggpht.com",
            "googlevideo.com",
            "google.com",
            "gstatic.com",
            "googleusercontent.com",
            "googleapis.com"
    );

    private SiteScope() {
    }

    public static boolean isInAppUrl(String url) {
        return normalizeInAppUrl(url) != null;
    }

    public static String normalizeInAppUrl(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.US);
        boolean isHttps = lower.startsWith("https://");
        boolean isHttp = lower.startsWith("http://");
        if (!isHttps && !isHttp) {
            return null;
        }
        String host = hostOf(lower);
        if (host == null) {
            return null;
        }
        for (String allowed : ALLOWED_HOSTS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return isHttps ? url : "https://" + url.substring(url.indexOf("://") + 3);
            }
        }
        return null;
    }

    private static String hostOf(String lowerUrl) {
        int start = lowerUrl.indexOf("://") + 3;
        int end = lowerUrl.length();
        for (int i = start; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = lowerUrl.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }
}
