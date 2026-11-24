package com.coplay.integration.support;

import com.coplay.user.api.MailService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mail outbox used by integration tests.
 */
public final class CapturingMailService implements MailService {

    private final Map<String, String> latestCodes = new ConcurrentHashMap<>();
    private final Map<String, Integer> sendCounts = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String email, String code) {
        latestCodes.put(email, code);
        sendCounts.merge(email, 1, Integer::sum);
    }

    public String latestCode(String email) {
        String code = latestCodes.get(email);
        if (code == null) {
            throw new IllegalStateException("No verification code for " + email);
        }
        return code;
    }

    public int sendCount(String email) {
        return sendCounts.getOrDefault(email, 0);
    }
}
