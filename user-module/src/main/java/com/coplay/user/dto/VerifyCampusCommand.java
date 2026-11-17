package com.coplay.user.dto;

/**
 * Input of the campus verification use case.
 */
public record VerifyCampusCommand(String email, String code) {
}
