package com.skystream.ssmusic;

import java.util.List;

/** Browser-like back/forward navigation over a WebView history list. */
public final class NavigationHistory {

    private NavigationHistory() {
    }

    public static int backSteps(List<String> urls, int currentIndex) {
        if (urls == null || currentIndex <= 0 || currentIndex >= urls.size()) {
            return 0;
        }
        String current = urls.get(currentIndex);
        int index = currentIndex - 1;
        while (index > 0 && sameEntry(urls.get(index), current)) {
            index--;
        }
        if (sameEntry(urls.get(index), current)) {
            return 0;
        }
        return index - currentIndex;
    }

    public static int forwardSteps(List<String> urls, int currentIndex) {
        if (urls == null || currentIndex < 0 || currentIndex >= urls.size() - 1) {
            return 0;
        }
        String current = urls.get(currentIndex);
        int last = urls.size() - 1;
        int index = currentIndex + 1;
        while (index < last && sameEntry(urls.get(index), current)) {
            index++;
        }
        if (sameEntry(urls.get(index), current)) {
            return 0;
        }
        return index - currentIndex;
    }

    private static boolean sameEntry(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
