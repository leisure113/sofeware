package com.coplay.user.dto;

/**
 * Input of the resend-code use case. The service method takes the email directly,
 * this record is kept for the future web layer.
 */
public record SendCodeCommand(String email) {
}
