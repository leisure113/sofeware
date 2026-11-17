package com.coplay.user.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory token port: issuance, uniqueness and rejection of unknown tokens.
 */
class InMemoryTokenServiceTest {

    private final InMemoryTokenService tokens = new InMemoryTokenService();

    @Test
    @DisplayName("an issued token resolves to the user it was issued for")
    void issuedTokenResolvesToUser() {
        String token = tokens.issue(1001L);

        assertFalse(token.isBlank());
        assertEquals(1001L, tokens.verify(token));
    }

    @Test
    @DisplayName("every issue call returns a distinct token and keeps the user mapping")
    void issuedTokensAreUnique() {
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            String token = tokens.issue(2000L + i);
            assertTrue(issued.add(token), "token was reused: " + token);
            assertEquals(2000L + i, tokens.verify(token));
        }
        assertEquals(25, issued.size());
    }

    @Test
    @DisplayName("verify rejects an unknown token with INVALID_TOKEN")
    void rejectsUnknownToken() {
        tokens.issue(1001L);

        BizException exception = assertThrows(BizException.class, () -> tokens.verify("not-a-real-token"));

        assertEquals(ErrorCode.INVALID_TOKEN.getCode(), exception.getCode());
        assertEquals(ErrorCode.INVALID_TOKEN.getMessage(), exception.getMessage());
    }

    @Test
    @DisplayName("verify rejects null and blank tokens")
    void rejectsNullOrBlankToken() {
        BizException nullToken = assertThrows(BizException.class, () -> tokens.verify(null));
        assertEquals(ErrorCode.INVALID_TOKEN.getCode(), nullToken.getCode());

        BizException blankToken = assertThrows(BizException.class, () -> tokens.verify(""));
        assertEquals(ErrorCode.INVALID_TOKEN.getCode(), blankToken.getCode());
    }
}
