package com.coplay.user.repository;

import com.coplay.user.domain.VerificationCode;

import java.util.Optional;

/**
 * Persistence port for campus verification codes. Only the newest code per email is kept,
 * so requesting a new code invalidates the previous one.
 */
public interface VerificationCodeRepository {

    VerificationCode save(VerificationCode verificationCode);

    Optional<VerificationCode> findLatestByEmail(String email);

    void deleteByEmail(String email);
}
