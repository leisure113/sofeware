package com.coplay.user.api;

/**
 * Password hashing port, so the algorithm can be replaced without touching the service.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
