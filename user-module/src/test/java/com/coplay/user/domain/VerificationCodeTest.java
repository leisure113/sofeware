package com.coplay.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification code value rules: attempt counter, expiry boundary and constant-time matching.
 */
class VerificationCodeTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 17, 10, 10);
    private static final String CODE = "123456";

    @Test
    @DisplayName("a new code starts at zero attempts and five remaining attempts")
    void newCodeStartsFresh() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT);

        assertEquals(1001L, code.getUserId());
        assertEquals("alice@coplay.edu", code.getEmail());
        assertEquals(CODE, code.getCode());
        assertEquals(EXPIRES_AT, code.getExpiresAt());
        assertEquals(0, code.getAttempts());
        assertEquals(VerificationCode.MAX_ATTEMPTS, code.remainingAttempts());
        assertEquals(5, VerificationCode.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("the attempt counter never reports negative remaining attempts")
    void incrementAttemptsIsBoundedAtZero() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT);

        for (int i = 0; i < 7; i++) {
            code.incrementAttempts();
        }

        assertEquals(7, code.getAttempts());
        assertEquals(0, code.remainingAttempts());
    }

    @Test
    @DisplayName("a code constructed with attempts already at the limit reports zero remaining")
    void codeCanBeRestoredWithAttempts() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT, 4);

        assertEquals(4, code.getAttempts());
        assertEquals(1, code.remainingAttempts());
    }

    @Test
    @DisplayName("expiry happens exactly at the expiry instant, not before")
    void expiryBoundary() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT);

        assertFalse(code.isExpired(EXPIRES_AT.minusNanos(1)));
        assertFalse(code.isExpired(EXPIRES_AT.minusSeconds(1)));
        assertTrue(code.isExpired(EXPIRES_AT));
        assertTrue(code.isExpired(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    @DisplayName("isExpired rejects a null clock value")
    void isExpiredRejectsNullNow() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT);

        assertThrows(NullPointerException.class, () -> code.isExpired(null));
    }

    @Test
    @DisplayName("matches compares the full code and rejects null, shorter and longer candidates")
    void matchesComparesFullCode() {
        VerificationCode code = new VerificationCode(1001L, "alice@coplay.edu", CODE, EXPIRES_AT);

        assertTrue(code.matches(CODE));
        assertFalse(code.matches(null));
        assertFalse(code.matches("12345"));
        assertFalse(code.matches("1234567"));
        assertFalse(code.matches("654321"));
        assertFalse(code.matches("123456 "));
    }
}
