package com.skystream.ssmusic;

import java.net.URI;
import java.net.URISyntaxException;

/** Native navigation boundary; the page script additionally verifies playlist membership. */
final class KidModeNavigation {
    static final String LIBRARY_URL = "https://music.youtube.com/library";

    private KidModeNavigation() {
    }

    static boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"music.youtube.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String path = uri.getRawPath();
            if (!uri.normalize().getRawPath().equals(path) || path.contains("%")) {
                return false;
            }
            if ("/library".equals(path) || path.startsWith("/library/")) {
                return true;
            }
            String playlist = Urls.queryParameterOf(url, "list");
            if (!isIdentifier(playlist) || playlist.regionMatches(true, 0, "RD", 0, 2)) {
                return false;
            }
            return "/playlist".equals(path)
                    || ("/watch".equals(path) && isIdentifier(Urls.queryParameterOf(url, "v")));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]+");
    }
}
