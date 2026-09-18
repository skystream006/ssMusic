package com.skystream.ssmusic;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class MusicServerUrls {
    private MusicServerUrls() {
    }

    static String playlistUrl(String pageUrl) {
        URI uri = musicUri(pageUrl);
        return uri != null && parameter(uri, "list") != null ? pageUrl : null;
    }

    static String songUrl(String pageUrl) {
        URI uri = musicUri(pageUrl);
        return uri != null && "/watch".equals(uri.getPath())
                ? songUrlForId(parameter(uri, "v")) : null;
    }

    static String songUrlForId(String videoId) {
        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}")
                ? "https://music.youtube.com/watch?v=" + videoId : null;
    }

    private static URI musicUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "music.youtube.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String parameter(URI uri, String key) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        String result = null;
        try {
            for (String part : uri.getRawQuery().split("&")) {
                String[] pair = part.split("=", 2);
                if (key.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()))) {
                    if (result != null || pair.length != 2 || pair[1].isEmpty()) {
                        return null;
                    }
                    result = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                }
            }
        } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
        return result;
    }
}
