package com.coplay.user.dto;

/**
 * Result of a successful login: the issued token plus the user profile.
 */
public record LoginResult(String token, UserProfile profile) {
}
