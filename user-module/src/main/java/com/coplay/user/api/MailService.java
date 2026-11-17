package com.coplay.user.api;

/**
 * Outbound mail port. The production implementation sends a real email,
 * the demo and tests use an in-memory outbox.
 */
public interface MailService {

    void sendVerificationCode(String email, String code);
}
