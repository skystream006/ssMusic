package com.skystream.ssmusic;

import java.net.MalformedURLException;
import java.net.URL;

final class Urls {

    private Urls() {
    }

    static String hostOf(String lowerUrl) {
        int schemeEnd = lowerUrl.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        int start = schemeEnd + 3;
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

    static String pathOf(String lowerUrl) {
        int schemeEnd = lowerUrl.indexOf("://");
        if (schemeEnd < 0) {
            return "";
        }
        int pathStart = lowerUrl.length();
        for (int i = schemeEnd + 3; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                pathStart = i;
                break;
            }
        }
        if (pathStart >= lowerUrl.length() || lowerUrl.charAt(pathStart) != '/') {
            return "";
        }
        int pathEnd = lowerUrl.length();
        for (int i = pathStart; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '?' || c == '#') {
                pathEnd = i;
                break;
            }
        }
        return lowerUrl.substring(pathStart, pathEnd);
    }

    static String queryParameterOf(String url, String name) {
        if (url == null || name == null) {
            return null;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        int queryEnd = url.indexOf('#', queryStart + 1);
        if (queryEnd < 0) {
            queryEnd = url.length();
        }
        int parameterStart = queryStart + 1;
        while (parameterStart < queryEnd) {
            int parameterEnd = url.indexOf('&', parameterStart);
            if (parameterEnd < 0 || parameterEnd > queryEnd) {
                parameterEnd = queryEnd;
            }
            String parameter = url.substring(parameterStart, parameterEnd);
            int valueStart = parameter.indexOf('=');
            String parameterName = valueStart < 0 ? parameter : parameter.substring(0, valueStart);
            if (parameterName.equals(name)) {
                return valueStart < 0 ? "" : parameter.substring(valueStart + 1);
            }
            parameterStart = parameterEnd + 1;
        }
        return null;
    }

    static boolean isAllowedThumbnailHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(java.util.Locale.US);
        return normalized.equals("music.youtube.com")
                || normalized.endsWith(".youtube.com")
                || normalized.endsWith(".ytimg.com")
                || normalized.endsWith(".ggpht.com")
                || normalized.endsWith(".googleusercontent.com")
                || normalized.endsWith(".gstatic.com");
    }

    static boolean isAllowedHttpsThumbnailUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            return isAllowedHttpsThumbnailUrl(new URL(value));
        } catch (MalformedURLException e) {
            return false;
        }
    }

    static boolean isAllowedHttpsThumbnailUrl(URL url) {
        return url != null
                && "https".equalsIgnoreCase(url.getProtocol())
                && isAllowedThumbnailHost(url.getHost());
    }
}
