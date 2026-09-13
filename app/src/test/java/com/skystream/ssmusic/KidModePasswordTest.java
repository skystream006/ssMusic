package com.skystream.ssmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KidModePasswordTest {
    @Test
    public void verifierIsSaltedAndOnlyUnlocksWithExactPassword() throws Exception {
        char[] password = "test passphrase \u2603".toCharArray();
        String first = KidModePassword.create(password);
        String second = KidModePassword.create(password);
        assertNotEquals(first, second);
        assertFalse(first.contains(new String(password)));
        assertTrue(KidModePassword.matches(password, first));
        assertFalse(KidModePassword.matches("wrong passphrase".toCharArray(), first));
        assertFalse(KidModePassword.matches(new char[0], first));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyPasswordCannotEnableKidMode() throws Exception {
        KidModePassword.create(new char[0]);
    }

    @Test
    public void missingOrCorruptVerifierNeverUnlocks() throws Exception {
        for (String verifier : new String[]{null, "", "password", "2:salt:hash",
                "1:00:00", "1:zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz:"
                + "0000000000000000000000000000000000000000000000000000000000000000"}) {
            assertFalse(KidModePassword.matches("test".toCharArray(), verifier));
        }
    }
}
