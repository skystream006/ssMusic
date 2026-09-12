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
            "ytimg.com",
            "ggpht.com",
            "googlevideo.com",
            "accounts.google.com",
            "gstatic.com",
            "googleusercontent.com",
            "apis.google.com"
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
        String host = Urls.hostOf(lower);
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

}
