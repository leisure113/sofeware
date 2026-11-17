package com.coplay.user.repository;

import com.coplay.user.domain.VerificationCode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory verification code repository. One code per email, a new save replaces the old one.
 */
public final class InMemoryVerificationCodeRepository implements VerificationCodeRepository {

    private final Map<String, VerificationCode> codes = new ConcurrentHashMap<>();

    @Override
    public VerificationCode save(VerificationCode verificationCode) {
        codes.put(verificationCode.getEmail(), verificationCode);
        return verificationCode;
    }

    @Override
    public Optional<VerificationCode> findLatestByEmail(String email) {
        return Optional.ofNullable(codes.get(email));
    }

    @Override
    public void deleteByEmail(String email) {
        codes.remove(email);
    }
}
