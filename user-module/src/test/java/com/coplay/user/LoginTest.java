package com.coplay.user;

import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.LoginResult;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login: unknown/inactive users, wrong credentials, email normalization and token issuance.
 */
class LoginTest extends UserServiceTestSupport {

    @Test
    @DisplayName("login rejects an unknown email and a null email with INVALID_CREDENTIALS")
    void rejectsUnknownUser() {
        BizException unknown = assertThrows(BizException.class,
                () -> service.login(new LoginCommand("ghost@coplay.edu", PASSWORD)));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), unknown.getCode());

        BizException nullEmail = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(null, PASSWORD)));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), nullEmail.getCode());
    }

    @Test
    @DisplayName("login rejects a user that is still pending verification")
    void rejectsPendingUser() {
        UserProfile profile = registerAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, PASSWORD)));

        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), exception.getCode());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("login rejects a muted user and a banned user")
    void rejectsMutedAndBannedUsers() {
        UserProfile profile = registerAndVerifyAlice();
        User admin = seedAdmin();

        service.changeStatus(admin.getId(), profile.id(), UserStatus.MUTED);
        BizException muted = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, PASSWORD)));
        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), muted.getCode());

        service.changeStatus(admin.getId(), profile.id(), UserStatus.BANNED);
        BizException banned = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, PASSWORD)));
        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), banned.getCode());
    }

    @Test
    @DisplayName("login rejects a null or wrong password with INVALID_CREDENTIALS")
    void rejectsWrongPassword() {
        registerAndVerifyAlice();

        BizException wrong = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, "wrong-password")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), wrong.getCode());

        BizException nullPassword = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, null)));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), nullPassword.getCode());
    }

    @Test
    @DisplayName("login normalizes the email case and padding")
    void normalizesEmail() {
        UserProfile profile = registerAndVerifyAlice();

        LoginResult result = service.login(new LoginCommand("  ALICE@COPLAY.EDU  ", PASSWORD));

        assertEquals(profile.id(), result.profile().id());
        assertEquals(EMAIL, result.profile().email());
        assertEquals(profile.id(), tokens.verify(result.token()));
    }

    @Test
    @DisplayName("each login issues a distinct token that resolves to the same user")
    void issuesDistinctTokensPerLogin() {
        UserProfile profile = registerAndVerifyAlice();

        LoginResult first = service.login(new LoginCommand(EMAIL, PASSWORD));
        LoginResult second = service.login(new LoginCommand(EMAIL, PASSWORD));

        assertNotEquals(first.token(), second.token());
        assertFalse(first.token().isBlank());
        assertFalse(second.token().isBlank());
        assertEquals(profile.id(), tokens.verify(first.token()));
        assertEquals(profile.id(), tokens.verify(second.token()));
    }

    @Test
    @DisplayName("login returns a profile snapshot without changing the account")
    void returnsProfileSnapshot() {
        UserProfile profile = registerAndVerifyAlice();

        LoginResult result = service.login(new LoginCommand(EMAIL, PASSWORD));

        UserProfile snapshot = result.profile();
        assertEquals(profile.id(), snapshot.id());
        assertEquals(STUDENT_NO, snapshot.studentNo());
        assertEquals(EMAIL, snapshot.email());
        assertEquals("Alice", snapshot.nickname());
        assertEquals(UserStatus.ACTIVE, snapshot.status());
        assertEquals(profile.role(), snapshot.role());
        assertTrue(service.isActive(profile.id()));
    }
}
