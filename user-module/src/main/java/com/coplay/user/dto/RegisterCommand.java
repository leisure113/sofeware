package com.coplay.user.dto;

/**
 * Input of the registration use case.
 */
public record RegisterCommand(String studentNo, String email, String password, String nickname) {
}
