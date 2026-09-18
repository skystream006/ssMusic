package com.skystream.ssmusic;

import org.junit.Test;

import java.security.SecureRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MusicServerAuthTest {
    private static final String STATE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";
    private static final String CODE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final long START = 1700000000000L;

    @Test
    public void normalizesHttpsOrigins() throws Exception {
        assertEquals("https://music.example.com",
                MusicServerAuth.normalizeOrigin("HTTPS://Music.Example.Com:443/"));
        assertEquals("https://localhost:8443",
                MusicServerAuth.normalizeOrigin("https://localhost:8443"));
        assertEquals("https://127.0.0.1", MusicServerAuth.normalizeOrigin("https://127.0.0.1/"));
        assertEquals("https://[::1]:8443", MusicServerAuth.normalizeOrigin("https://[::1]:8443/"));
    }

    @Test
    public void rejectsNonOriginAndAmbiguousInputs() {
        for (String value : new String[]{null, "", "http://music.example.com",
                "https://", "//music.example.com", "https://user@music.example.com",
                "https://music.example.com/path", "https://music.example.com//",
                "https://music.example.com?", "https://music.example.com#",
                "https://music.example.com:0", "https://music.example.com:65536",
                "https://music.example.com:", "https://music.example.com:-1",
                "https://music.example.com:0443", "https://music.example.com\\@evil.test",
                "https://music.example.com\n", " https://music.example.com",
                "https://%6dusic.example.com", "https://music.example.com/%2e",
                "https://music.example.com/?token=value"}) {
            try {
                MusicServerAuth.normalizeOrigin(value);
                fail("Accepted invalid origin");
            } catch (Exception expected) {
                assertEquals("Enter an HTTPS server origin, such as https://music.example.com.",
                        expected.getMessage());
            }
        }
    }

    @Test
    public void pkceMatchesRfc7636Vector() throws Exception {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                MusicServerAuth.challenge(VERIFIER));
        String url = MusicServerAuth.loginUrl(pending());
        assertEquals("https://music.example.com/app-login?"
                + "redirect_uri=com.ssytdlp.app%3A%2Foauth%2Fcallback"
                + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                + "&code_challenge_method=S256&state=" + STATE, url);
    }

    @Test
    public void independentlyGeneratesFullEntropyVerifierAndState() throws Exception {
        final int[] calls = {0};
        SecureRandom random = new SecureRandom() {
            @Override public void nextBytes(byte[] bytes) {
                assertEquals(32, bytes.length);
                java.util.Arrays.fill(bytes, (byte) ++calls[0]);
            }
        };
        MusicServerAuth.Pending pending =
                MusicServerAuth.newPending("https://music.example.com", START, random);
        assertEquals(2, calls[0]);
        assertTrue(pending.verifier.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(pending.state.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(pending.verifier, pending.state);
    }

    @Test
    public void callbackAcceptsEitherParameterOrderOnlyOnce() throws Exception {
        for (String value : new String[]{callback(), MusicServerAuth.REDIRECT_URI
                + "?state=" + STATE + "&code=" + CODE}) {
            MusicServerAuth.Pending pending = pending();
            assertEquals(CODE, pending.consumeCallback(value, START + 1));
            reject(pending, value, START + 2);
        }
    }

    @Test
    public void invalidCallbacksDoNotConsumePendingLogin() throws Exception {
        String[] invalid = {
                null, "", "https://music.example.com/oauth/callback?code=" + CODE + "&state=" + STATE,
                callback().replace("com.ssytdlp.app:", "com.ssmusic.app:"),
                callback().replace("com.ssytdlp.app:", "COM.SSYTDLP.APP:"),
                callback().replace(":/oauth", ":///oauth"),
                callback().replace(":/oauth", "://host/oauth"),
                callback().replace("/oauth/callback", "/oauth/callback/"),
                callback().replace("/oauth/callback", "/oauth/%63allback"),
                callback() + "#", callback() + "#fragment", callback() + "&",
                callback() + "&state=" + STATE, callback() + "&code=" + CODE,
                callback() + "&unknown=value",
                MusicServerAuth.REDIRECT_URI + "?code=" + CODE,
                MusicServerAuth.REDIRECT_URI + "?state=" + STATE,
                MusicServerAuth.REDIRECT_URI + "?code=&state=" + STATE,
                MusicServerAuth.REDIRECT_URI + "?code=" + CODE + "&state=",
                callback().replace(CODE, CODE + "a"),
                callback().replace(CODE, CODE.substring(1)),
                callback().replace("code=", "Code="),
                callback().replace(CODE, "%" + CODE),
                callback().replace(STATE, STATE.substring(0, 42) + "!"),
                callback().replace(STATE, STATE.substring(0, 42) + "x"),
                callback().replace("state=", "state= "),
                MusicServerAuth.REDIRECT_URI + "?code=" + CODE + "&code=" + CODE
        };
        for (String value : invalid) {
            MusicServerAuth.Pending pending = pending();
            reject(pending, value, START + 1);
            assertEquals(CODE, pending.consumeCallback(callback(), START + 2));
        }
    }

    @Test
    public void expiryRejectsBoundaryAndClockRollback() throws Exception {
        assertFalse(pending().isExpired(START + MusicServerAuth.LOGIN_LIFETIME_MS - 1));
        assertTrue(pending().isExpired(START + MusicServerAuth.LOGIN_LIFETIME_MS));
        reject(pending(), callback(), START + MusicServerAuth.LOGIN_LIFETIME_MS);
        reject(pending(), callback(), START - 1);
    }

    @Test
    public void restoredPendingUsesOriginalExpiryAndState() throws Exception {
        MusicServerAuth.Pending first = pending();
        MusicServerAuth.Pending restored = new MusicServerAuth.Pending(
                first.origin, first.verifier, first.state, first.startedAt);
        assertEquals(CODE, restored.consumeCallback(callback(), START + 100));
        reject(new MusicServerAuth.Pending(first.origin, first.verifier,
                first.state, first.startedAt), callback(), START + MusicServerAuth.LOGIN_LIFETIME_MS);
    }

    @Test
    public void parsesIsoExpiryWithoutApi26TimeClasses() throws Exception {
        assertEquals(1700000000000L, MusicServerAuth.parseExpiry("2023-11-14T22:13:20Z"));
        assertEquals(1700000000123L, MusicServerAuth.parseExpiry("2023-11-14T22:13:20.123Z"));
        assertEquals(1700000000100L, MusicServerAuth.parseExpiry("2023-11-14T22:13:20.1Z"));
        assertEquals(1700000000120L, MusicServerAuth.parseExpiry("2023-11-14T22:13:20.12Z"));
        assertEquals(1700000000000L, MusicServerAuth.parseExpiry("2023-11-15T00:13:20+02:00"));
        for (String value : new String[]{null, "", "2023-02-30T22:13:20Z",
                "2023-11-14T22:13:20", "2023-11-14T22:13:20Zgarbage", "1700000000000"}) {
            try {
                MusicServerAuth.parseExpiry(value);
                fail("Accepted invalid expiry");
            } catch (Exception expected) {
                assertEquals("Invalid session expiry.", expected.getMessage());
            }
        }
    }

    private static MusicServerAuth.Pending pending() throws Exception {
        return new MusicServerAuth.Pending("https://music.example.com", VERIFIER, STATE, START);
    }

    private static String callback() {
        return MusicServerAuth.REDIRECT_URI + "?code=" + CODE + "&state=" + STATE;
    }

    private static void reject(MusicServerAuth.Pending pending, String callback, long now) {
        try {
            pending.consumeCallback(callback, now);
            fail("Accepted invalid or replayed callback");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().startsWith("Invalid login response.")
                    || expected.getMessage().startsWith("Login expired."));
        }
    }
}
