package com.coplay.user.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PBKDF2 hasher contract: stored format, fresh salts, verification and malformed input handling.
 */
class Pbkdf2PasswordHasherTest {

    private static final String PASSWORD = "alice12345";
    private static final int FAST_ITERATIONS = 1_000;

    @Test
    @DisplayName("hash uses the documented format with a 16-byte salt and a 32-byte derived key")
    void hashUsesDocumentedFormat() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(FAST_ITERATIONS);

        String hash = hasher.hash(PASSWORD);
        String[] parts = hash.split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals(String.valueOf(FAST_ITERATIONS), parts[1]);
        assertEquals(16, Base64.getDecoder().decode(parts[2]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[3]).length);
        assertTrue(hasher.matches(PASSWORD, hash));
    }

    @Test
    @DisplayName("every hash uses a fresh salt so identical passwords produce different stored values")
    void usesFreshSaltPerHash() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(FAST_ITERATIONS);

        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        assertNotEquals(first, second);
        assertTrue(hasher.matches(PASSWORD, first));
        assertTrue(hasher.matches(PASSWORD, second));
    }

    @Test
    @DisplayName("the default hasher records 120000 iterations in the stored format")
    void defaultHasherUses120000Iterations() {
        String hash = new Pbkdf2PasswordHasher().hash(PASSWORD);

        assertEquals("120000", hash.split("\\$")[1]);
        assertTrue(new Pbkdf2PasswordHasher().matches(PASSWORD, hash));
    }

    @Test
    @DisplayName("matches reads the iteration count from the stored hash instead of the instance")
    void matchesUsesStoredIterations() {
        String hash = new Pbkdf2PasswordHasher(FAST_ITERATIONS).hash(PASSWORD);

        assertTrue(new Pbkdf2PasswordHasher(FAST_ITERATIONS * 2).matches(PASSWORD, hash));
    }

    @Test
    @DisplayName("matches rejects a wrong password and a structurally valid but unrelated hash")
    void rejectsWrongPassword() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(FAST_ITERATIONS);
        String hash = hasher.hash(PASSWORD);

        assertFalse(hasher.matches("alice12346", hash));
        assertFalse(hasher.matches("", hash));
        assertFalse(hasher.matches(PASSWORD, "pbkdf2-sha256$1000$c2FsdA==$c2hvcnQ="));
    }

    @Test
    @DisplayName("matches returns false for null raw password or null stored hash")
    void nullArgumentsDoNotMatch() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(FAST_ITERATIONS);
        String hash = hasher.hash(PASSWORD);

        assertFalse(hasher.matches(null, hash));
        assertFalse(hasher.matches(PASSWORD, null));
        assertFalse(hasher.matches(null, null));
    }

    @Test
    @DisplayName("matches returns false for unknown prefixes, wrong part counts and invalid numbers")
    void rejectsMalformedHashes() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(FAST_ITERATIONS);

        String[] malformed = {
                "not-a-hash",
                "bcrypt$120000$c2FsdA==$aGFzaA==",
                "pbkdf2-sha256$not-a-number$c2FsdA==$aGFzaA==",
                "pbkdf2-sha256$1000$not base64!$aGFzaA==",
                "pbkdf2-sha256$1000$c2FsdA==$not base64!",
                "pbkdf2-sha256$0$c2FsdA==$aGFzaA==",
                "pbkdf2-sha256$1000$c2FsdA==$aGFzaA==$extra"
        };

        for (String candidate : malformed) {
            assertFalse(hasher.matches(PASSWORD, candidate), candidate);
        }
    }

    @Test
    @DisplayName("the constructor rejects a non-positive iteration count")
    void rejectsNonPositiveIterations() {
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2PasswordHasher(0));
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2PasswordHasher(-1));
    }

    @Test
    @DisplayName("hash rejects a null password")
    void hashRejectsNullPassword() {
        assertThrows(NullPointerException.class, () -> new Pbkdf2PasswordHasher(FAST_ITERATIONS).hash(null));
    }
}
