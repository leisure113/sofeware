package com.coplay.user;

import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD tests for the new change-password use case (written before the implementation existed).
 */
class ChangePasswordTest extends UserServiceTestSupport {

    @Test
    @DisplayName("changePassword stores the new hash so the new password logs in and the old one is rejected")
    void changesPasswordForExistingUser() {
        UserProfile profile = registerAndVerifyAlice();
        assertTrue(hasher.matches(PASSWORD, storedHash(profile.id())));

        service.changePassword(profile.id(), PASSWORD, NEW_PASSWORD);

        String updatedHash = storedHash(profile.id());
        assertTrue(hasher.matches(NEW_PASSWORD, updatedHash));
        assertFalse(hasher.matches(PASSWORD, updatedHash));

        assertEquals(profile.id(),
                service.login(new LoginCommand(EMAIL, NEW_PASSWORD)).profile().id());

        BizException oldPasswordLogin = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, PASSWORD)));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), oldPasswordLogin.getCode());
    }

    @Test
    @DisplayName("changePassword rejects a wrong old password and leaves the stored hash untouched")
    void rejectsWrongOldPassword() {
        UserProfile profile = registerAndVerifyAlice();
        String originalHash = storedHash(profile.id());

        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(profile.id(), "not-the-old-password", NEW_PASSWORD));

        assertEquals(ErrorCode.INVALID_OLD_PASSWORD.getCode(), exception.getCode());
        assertEquals(originalHash, storedHash(profile.id()));
        assertTrue(hasher.matches(PASSWORD, storedHash(profile.id())));
    }

    @Test
    @DisplayName("changePassword rejects a new password shorter than 8 characters and keeps the old one")
    void rejectsWeakNewPassword() {
        UserProfile profile = registerAndVerifyAlice();
        String originalHash = storedHash(profile.id());

        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(profile.id(), PASSWORD, "1234567"));

        assertEquals(ErrorCode.WEAK_PASSWORD.getCode(), exception.getCode());
        assertEquals(originalHash, storedHash(profile.id()));
        assertTrue(hasher.matches(PASSWORD, storedHash(profile.id())));
    }

    @Test
    @DisplayName("changePassword rejects an unknown user")
    void rejectsUnknownUser() {
        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(UNKNOWN_USER_ID, PASSWORD, NEW_PASSWORD));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("changePassword accepts a new password of exactly 8 characters")
    void acceptsEightCharacterNewPassword() {
        UserProfile profile = registerAndVerifyAlice();

        service.changePassword(profile.id(), PASSWORD, "12345678");

        assertTrue(hasher.matches("12345678", storedHash(profile.id())));
        assertEquals(profile.id(),
                service.login(new LoginCommand(EMAIL, "12345678")).profile().id());
    }

    @Test
    @DisplayName("changePassword treats a null new password as weak")
    void rejectsNullNewPassword() {
        UserProfile profile = registerAndVerifyAlice();
        String originalHash = storedHash(profile.id());

        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(profile.id(), PASSWORD, null));

        assertEquals(ErrorCode.WEAK_PASSWORD.getCode(), exception.getCode());
        assertEquals(originalHash, storedHash(profile.id()));
    }

    @Test
    @DisplayName("changePassword treats a null old password as a wrong old password")
    void rejectsNullOldPassword() {
        UserProfile profile = registerAndVerifyAlice();
        String originalHash = storedHash(profile.id());

        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(profile.id(), null, NEW_PASSWORD));

        assertEquals(ErrorCode.INVALID_OLD_PASSWORD.getCode(), exception.getCode());
        assertEquals(originalHash, storedHash(profile.id()));
    }

    @Test
    @DisplayName("the old password is verified before the new password is validated")
    void verifiesOldPasswordBeforeNewPassword() {
        UserProfile profile = registerAndVerifyAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(profile.id(), "wrong-old-password", "short"));

        assertEquals(ErrorCode.INVALID_OLD_PASSWORD.getCode(), exception.getCode());
        assertTrue(hasher.matches(PASSWORD, storedHash(profile.id())));
    }
}
