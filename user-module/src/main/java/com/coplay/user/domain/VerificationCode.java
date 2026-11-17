package com.coplay.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One campus verification code. Codes are single-use and expire after ten minutes.
 * The attempt counter is raised on every wrong submission so brute force is bounded.
 */
public final class VerificationCode {

    public static final int MAX_ATTEMPTS = 5;

    private final long userId;
    private final String email;
    private final String code;
    private final LocalDateTime expiresAt;
    private int attempts;

    public VerificationCode(long userId, String email, String code, LocalDateTime expiresAt) {
        this(userId, email, code, expiresAt, 0);
    }

    public VerificationCode(long userId, String email, String code, LocalDateTime expiresAt, int attempts) {
        this.userId = userId;
        this.email = Objects.requireNonNull(email, "email");
        this.code = Objects.requireNonNull(code, "code");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.attempts = attempts;
    }

    public long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    /**
     * A code is expired once the clock reaches its expiry instant.
     */
    public boolean isExpired(LocalDateTime now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    public void incrementAttempts() {
        attempts++;
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }
}
