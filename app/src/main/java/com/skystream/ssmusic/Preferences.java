package com.skystream.ssmusic;

import java.util.Locale;

/**
 * User preference values and the URL/user-agent rules that depend on them. Kept free of
 * Android dependencies so it can be unit tested on the JVM.
 */
public final class Preferences {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private static final String HOME_URL = "https://music.youtube.com/";
    private static final String WATCH_URL = "https://music.youtube.com/watch";

    private Preferences() {
    }

    public static String userAgent(boolean desktopMode) {
        return desktopMode ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT;
    }

    public static String homeUrl() {
        return HOME_URL;
    }

    public static String restoreUrl(String savedUrl) {
        String normalized = SiteScope.normalizeInAppUrl(savedUrl);
        return SiteScope.isPlaybackUrl(normalized) ? normalized : HOME_URL;
    }

    public static String playbackIdentityUrl(String url) {
        String normalized = SiteScope.normalizeInAppUrl(url);
        if (!SiteScope.isPlaybackUrl(normalized)
                || !"/watch".equals(Urls.pathOf(normalized.toLowerCase(Locale.US)))) {
            return null;
        }
        String video = Urls.queryParameterOf(normalized, "v");
        if (video == null || video.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder(WATCH_URL).append("?v=").append(video);
        String playlist = Urls.queryParameterOf(normalized, "list");
        if (playlist != null && !playlist.isEmpty()) {
            result.append("&list=").append(playlist);
        }
        return result.toString();
    }

    public static String buildPersistedPlaybackUrl(String url, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            return null;
        }
        String identity = playbackIdentityUrl(url);
        if (identity == null) {
            return null;
        }
        return identity + "&t=" + (long) Math.floor(seconds);
    }

    public static boolean isSamePlaybackItem(String firstUrl, String secondUrl) {
        String first = playbackIdentityUrl(firstUrl);
        String second = playbackIdentityUrl(secondUrl);
        return first != null && first.equals(second);
    }
}
