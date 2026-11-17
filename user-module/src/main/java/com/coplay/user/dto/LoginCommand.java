package com.coplay.user.dto;

/**
 * Input of the login use case.
 */
public record LoginCommand(String email, String password) {
}
