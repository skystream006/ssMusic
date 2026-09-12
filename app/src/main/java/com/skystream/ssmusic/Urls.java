package com.skystream.ssmusic;

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
}
