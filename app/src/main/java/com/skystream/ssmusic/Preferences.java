package com.skystream.ssmusic;

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

    private Preferences() {
    }

    public static String userAgent(boolean desktopMode) {
        return desktopMode ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT;
    }

    public static String homeUrl() {
        return HOME_URL;
    }
}
