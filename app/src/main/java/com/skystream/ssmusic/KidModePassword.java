package com.skystream.ssmusic;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores a salted verifier, never the password. PBKDF2-HMAC-SHA1 supports API 21. */
final class KidModePassword {
    static final String PREFERENCE = "kid_mode_password_verifier";
    private static final int ITERATIONS = 1_300_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private KidModePassword() {
    }

    static String create(char[] password) throws GeneralSecurityException {
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return "1:" + hex(salt) + ":" + hex(derive(password, salt));
    }

    static boolean matches(char[] password, String verifier) throws GeneralSecurityException {
        if (password.length == 0 || verifier == null) {
            return false;
        }
        String[] parts = verifier.split(":", -1);
        if (parts.length != 3 || !"1".equals(parts[0])
                || parts[1].length() != SALT_BYTES * 2 || parts[2].length() != HASH_BYTES * 2) {
            return false;
        }
        try {
            byte[] expected = unhex(parts[2]);
            byte[] actual = derive(password, unhex(parts[1]));
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value & 0xff) >>> 4, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static byte[] unhex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid verifier");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
