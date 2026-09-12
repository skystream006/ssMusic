package com.skystream.ssmusic;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Decides which network requests made by YouTube Music should be blocked. */
public final class AdBlocker {

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "doubleclick.net",
            "adnxs.com",
            "adsrvr.org",
            "googleadservices.com",
            "googlesyndication.com",
            "google-analytics.com",
            "googletagservices.com",
            "googletagmanager.com",
            "adservice.google.com",
            "ads.youtube.com"
    );

    private static final List<String> BLOCKED_PATHS = Arrays.asList(
            "/pagead/",
            "/ads/",
            "/ptracking",
            "/api/stats/ads",
            "/get_midroll_",
            "/pcs/activeview",
            "/generate_ad",
            "/ad_companion",
            "/youtubei/v1/ads"
    );

    private static final List<String> BLOCKED_QUERY_PARAMETERS = Arrays.asList(
            "ad_format",
            "ad_type",
            "ad_slot",
            "adurl",
            "google_ad_client",
            "google_ad_slot"
    );

    private AdBlocker() {
    }

    public static boolean isAd(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        String host = Urls.hostOf(lower);
        if (host != null) {
            for (String blocked : BLOCKED_HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
            }
        }
        String requestPath = Urls.pathOf(lower);
        for (String path : BLOCKED_PATHS) {
            if (requestPath.contains(path)) {
                return true;
            }
        }
        return hasBlockedQueryParameter(lower);
    }

    private static boolean hasBlockedQueryParameter(String lowerUrl) {
        int queryStart = lowerUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        int fragmentStart = lowerUrl.indexOf('#', queryStart);
        String query = lowerUrl.substring(queryStart + 1,
                fragmentStart < 0 ? lowerUrl.length() : fragmentStart);
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String name = separator < 0 ? parameter : parameter.substring(0, separator);
            if (BLOCKED_QUERY_PARAMETERS.contains(name)) {
                return true;
            }
        }
        return false;
    }

}
