package com.skystream.ssmusic;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class MusicServerAuth {
    static final String REDIRECT_URI = "com.ssytdlp.app:/oauth/callback";
    static final long LOGIN_LIFETIME_MS = 10 * 60 * 1000L;
    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private MusicServerAuth() {}

    static String normalizeOrigin(String value) throws Exception {
        if (value == null || value.length() > 2048) throw invalidOrigin();
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception ignored) {
            throw invalidOrigin();
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isEmpty()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.isOpaque()
                || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                || port == 0 || port > 65535 || port < -1
                || !uri.getRawAuthority().equals(host + (port == -1 ? "" : ":" + port))) {
            throw invalidOrigin();
        }
        return "https://" + host.toLowerCase(Locale.ROOT)
                + (port == -1 || port == 443 ? "" : ":" + port);
    }

    private static IllegalArgumentException invalidOrigin() {
        return new IllegalArgumentException("Enter an HTTPS server origin, such as https://music.example.com.");
    }

    static Pending newPending(String origin, long now, SecureRandom random) throws Exception {
        return new Pending(normalizeOrigin(origin), randomToken(random), randomToken(random), now);
    }

    static String randomToken(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return base64Url(bytes);
    }

    static String base64Url(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 4 + 2) / 3);
        int bits = 0;
        int count = 0;
        for (byte value : bytes) {
            bits = (bits << 8) | (value & 255);
            count += 8;
            while (count >= 6) {
                count -= 6;
                result.append(BASE64[(bits >>> count) & 63]);
            }
        }
        if (count > 0) result.append(BASE64[(bits << (6 - count)) & 63]);
        return result.toString();
    }

    static String challenge(String verifier) throws Exception {
        if (verifier == null || !verifier.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new Exception("Invalid login request.");
        }
        return base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    static String loginUrl(Pending pending) throws Exception {
        return pending.origin + "/app-login?redirect_uri="
                + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&code_challenge=" + challenge(pending.verifier)
                + "&code_challenge_method=S256&state=" + pending.state;
    }

    static long parseExpiry(String value) throws Exception {
        if (value == null || !value.matches(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?(Z|[+-]\\d{2}:\\d{2})")) {
            throw new Exception("Invalid session expiry.");
        }
        String normalized = value;
        int dot = normalized.indexOf('.');
        if (dot >= 0) {
            int end = dot + 1;
            while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) end++;
            String fraction = (normalized.substring(dot + 1, end) + "00").substring(0, 3);
            normalized = normalized.substring(0, dot + 1) + fraction + normalized.substring(end);
        }
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        } else {
            int colon = normalized.lastIndexOf(':');
            normalized = normalized.substring(0, colon) + normalized.substring(colon + 1);
        }
        SimpleDateFormat format = new SimpleDateFormat(
                dot >= 0 ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ",
                Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(normalized, position);
        if (date == null || position.getIndex() != normalized.length()) {
            throw new Exception("Invalid session expiry.");
        }
        return date.getTime();
    }

    static final class Pending {
        final String origin;
        final String verifier;
        final String state;
        final long startedAt;
        private boolean consumed;

        Pending(String origin, String verifier, String state, long startedAt) throws Exception {
            this.origin = normalizeOrigin(origin);
            if (verifier == null || !verifier.matches("[A-Za-z0-9_-]{43,128}")
                    || state == null || !state.matches("[A-Za-z0-9_-]{43,128}")) {
                throw new Exception("Invalid login request.");
            }
            this.verifier = verifier;
            this.state = state;
            this.startedAt = startedAt;
        }

        boolean isExpired(long now) {
            return now < startedAt || now - startedAt >= LOGIN_LIFETIME_MS;
        }

        synchronized String consumeCallback(String value, long now) throws Exception {
            if (consumed || isExpired(now)) throw new Exception("Login expired. Please sign in again.");
            if (value == null || value.length() > 2048
                    || !value.startsWith(REDIRECT_URI + "?")) throw invalidCallback();
            URI uri;
            try {
                uri = new URI(value);
            } catch (Exception ignored) {
                throw invalidCallback();
            }
            if (!"com.ssytdlp.app".equals(uri.getScheme()) || uri.getRawAuthority() != null
                    || !"/oauth/callback".equals(uri.getRawPath())
                    || uri.getRawFragment() != null) throw invalidCallback();
            String code = null;
            String returnedState = null;
            String[] parameters = uri.getRawQuery().split("&", -1);
            if (parameters.length != 2) throw invalidCallback();
            for (String parameter : parameters) {
                if (parameter.startsWith("code=") && code == null) {
                    code = parameter.substring(5);
                } else if (parameter.startsWith("state=") && returnedState == null) {
                    returnedState = parameter.substring(6);
                } else {
                    throw invalidCallback();
                }
            }
            if (code == null || !code.matches("[A-Za-z0-9_-]{43}")
                    || returnedState == null || !returnedState.matches("[A-Za-z0-9_-]{43,128}")
                    || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                            returnedState.getBytes(StandardCharsets.US_ASCII))) throw invalidCallback();
            consumed = true;
            return code;
        }

        private Exception invalidCallback() {
            return new Exception("Invalid login response. Return to the browser to finish signing in.");
        }
    }
}
