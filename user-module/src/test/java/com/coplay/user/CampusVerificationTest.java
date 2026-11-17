package com.coplay.user;

import com.coplay.user.domain.UserStatus;
import com.coplay.user.domain.VerificationCode;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.service.UserServiceImpl;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Campus verification: unknown users, missing/expired/wrong codes, attempt limit, single use and resend.
 */
class CampusVerificationTest extends UserServiceTestSupport {

    @Test
    @DisplayName("verifyCampus rejects an unknown email and a null email")
    void rejectsUnknownUser() {
        BizException unknown = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand("ghost@coplay.edu", "123456")));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), unknown.getCode());

        BizException nullEmail = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(null, "123456")));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), nullEmail.getCode());
    }

    @Test
    @DisplayName("verifyCampus rejects a code when no code was ever issued for the user")
    void rejectsWhenNoCodeExists() {
        UserProfile profile = registerAlice();
        codes.deleteByEmail(EMAIL);

        BizException exception = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, mail.lastCode(EMAIL))));

        assertEquals(ErrorCode.INVALID_CODE.getCode(), exception.getCode());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("verifyCampus counts a null code as a wrong attempt")
    void rejectsNullCodeAndCountsAttempt() {
        registerAlice();

        BizException exception = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, null)));

        assertEquals(ErrorCode.INVALID_CODE.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("4 attempts left"), exception::getMessage);
        assertEquals(1, storedCode().getAttempts());
        assertEquals(4, storedCode().remainingAttempts());
    }

    @Test
    @DisplayName("wrong code attempts 1..5 increment the counter and report the remaining attempts")
    void wrongCodeAttemptsFromOneToFive() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);

        for (int attempt = 1; attempt <= VerificationCode.MAX_ATTEMPTS; attempt++) {
            int remaining = VerificationCode.MAX_ATTEMPTS - attempt;
            BizException exception = assertThrows(BizException.class, () -> service.verifyCampus(
                    new VerifyCampusCommand(EMAIL, wrongCode(code))));

            assertEquals(ErrorCode.INVALID_CODE.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains(remaining + " attempts left"), exception::getMessage);
            assertEquals(attempt, storedCode().getAttempts());
            assertEquals(remaining, storedCode().remainingAttempts());
        }

        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("after five wrong attempts even the correct code is rejected with TOO_MANY_ATTEMPTS")
    void tooManyAttemptsRejectsCorrectCode() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);
        exhaustAttempts(code);

        BizException exception = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, code)));

        assertEquals(ErrorCode.TOO_MANY_ATTEMPTS.getCode(), exception.getCode());
        assertEquals(VerificationCode.MAX_ATTEMPTS, storedCode().getAttempts());
        assertEquals(0, storedCode().remainingAttempts());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("the fifth attempt may still succeed: four wrong attempts then the correct code activates")
    void correctCodeStillAcceptedOnFifthAttempt() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);
        for (int attempt = 1; attempt < VerificationCode.MAX_ATTEMPTS; attempt++) {
            assertThrows(BizException.class, () -> service.verifyCampus(
                    new VerifyCampusCommand(EMAIL, wrongCode(code))));
        }

        UserProfile verified = service.verifyCampus(new VerifyCampusCommand(EMAIL, code));

        assertEquals(UserStatus.ACTIVE, verified.status());
        assertTrue(service.isActive(profile.id()));
    }

    @Test
    @DisplayName("a code is still valid one second before expiry and expired exactly at the expiry instant")
    void expiryBoundary() {
        registerAlice();
        String code = mail.lastCode(EMAIL);

        UserServiceImpl oneSecondBeforeExpiry = serviceAt(Duration.ofMinutes(10).minusSeconds(1));
        UserProfile verified = oneSecondBeforeExpiry.verifyCampus(new VerifyCampusCommand(EMAIL, code));

        assertEquals(UserStatus.ACTIVE, verified.status());
        assertTrue(codes.findLatestByEmail(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("a code submitted exactly ten minutes after issue is rejected as expired")
    void expiredExactlyAtExpiryInstant() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);

        UserServiceImpl atExpiry = serviceAt(Duration.ofMinutes(10));
        BizException exception = assertThrows(BizException.class,
                () -> atExpiry.verifyCampus(new VerifyCampusCommand(EMAIL, code)));

        assertEquals(ErrorCode.CODE_EXPIRED.getCode(), exception.getCode());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
        assertTrue(codes.findLatestByEmail(EMAIL).isPresent());
    }

    @Test
    @DisplayName("expiry is checked before the attempt limit")
    void expiryWinsOverTooManyAttempts() {
        registerAlice();
        String code = mail.lastCode(EMAIL);
        exhaustAttempts(code);

        UserServiceImpl expiredService = serviceAt(Duration.ofMinutes(11));
        BizException exception = assertThrows(BizException.class,
                () -> expiredService.verifyCampus(new VerifyCampusCommand(EMAIL, code)));

        assertEquals(ErrorCode.CODE_EXPIRED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("a successful verification consumes the code so it cannot be replayed")
    void verificationIsSingleUse() {
        registerAlice();
        String code = mail.lastCode(EMAIL);
        service.verifyCampus(new VerifyCampusCommand(EMAIL, code));

        assertTrue(codes.findLatestByEmail(EMAIL).isEmpty());
        BizException replay = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, code)));
        assertEquals(ErrorCode.INVALID_CODE.getCode(), replay.getCode());
    }

    @Test
    @DisplayName("resending a code resets attempts and extends the expiry from the current clock")
    void resendResetsAttemptsAndExtendsExpiry() {
        registerAlice();
        String firstCode = mail.lastCode(EMAIL);
        assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, wrongCode(firstCode))));
        assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, wrongCode(firstCode))));
        assertEquals(2, storedCode().getAttempts());

        UserServiceImpl twoMinutesLater = serviceAt(Duration.ofMinutes(2));
        twoMinutesLater.sendVerificationCode(EMAIL);

        String resentCode = mail.lastCode(EMAIL);
        assertEquals(2, mail.sentCount());
        assertEquals(resentCode, storedCode().getCode());
        assertEquals(NOW.plusMinutes(12), storedCode().getExpiresAt());
        assertEquals(0, storedCode().getAttempts());

        UserProfile verified = twoMinutesLater.verifyCampus(new VerifyCampusCommand(EMAIL, resentCode));
        assertEquals(UserStatus.ACTIVE, verified.status());
    }

    @Test
    @DisplayName("sendVerificationCode rejects unknown, null and blank emails")
    void sendCodeRejectsUnknownUsers() {
        BizException unknown = assertThrows(BizException.class,
                () -> service.sendVerificationCode("ghost@coplay.edu"));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), unknown.getCode());

        BizException nullEmail = assertThrows(BizException.class, () -> service.sendVerificationCode(null));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), nullEmail.getCode());

        BizException blankEmail = assertThrows(BizException.class, () -> service.sendVerificationCode("   "));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), blankEmail.getCode());

        assertEquals(0, mail.sentCount());
    }

    @Test
    @DisplayName("sendVerificationCode and verifyCampus normalize the email before looking the user up")
    void normalizesEmailOnLookup() {
        registerAlice();
        service.sendVerificationCode("  ALICE@COPLAY.EDU  ");

        assertEquals(2, mail.sentCount());
        assertEquals(storedCode().getCode(), mail.lastCode(EMAIL));

        UserProfile verified = service.verifyCampus(new VerifyCampusCommand(" Alice@Coplay.Edu ", mail.lastCode(EMAIL)));
        assertEquals(UserStatus.ACTIVE, verified.status());
        assertFalse(service.isMuted(verified.id()));
    }

    private void exhaustAttempts(String code) {
        for (int attempt = 0; attempt < VerificationCode.MAX_ATTEMPTS; attempt++) {
            assertThrows(BizException.class, () -> service.verifyCampus(
                    new VerifyCampusCommand(EMAIL, wrongCode(code))));
        }
    }
}
