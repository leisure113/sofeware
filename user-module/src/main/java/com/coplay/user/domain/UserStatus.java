package com.coplay.user.domain;

/**
 * Lifecycle of a campus user account.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    MUTED,
    BANNED
}
