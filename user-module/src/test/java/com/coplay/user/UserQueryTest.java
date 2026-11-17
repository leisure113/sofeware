package com.coplay.user;

import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.RegisterCommand;
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
 * Read-side queries used by other modules: profile lookup and the three boolean predicates.
 */
class UserQueryTest extends UserServiceTestSupport {

    @Test
    @DisplayName("getProfile rejects an unknown user id")
    void getProfileRejectsUnknownUser() {
        BizException exception = assertThrows(BizException.class,
                () -> service.getProfile(UNKNOWN_USER_ID));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("getProfile returns a safe profile snapshot for an existing user")
    void getProfileReturnsSnapshot() {
        UserProfile profile = registerAlice();

        UserProfile loaded = service.getProfile(profile.id());

        assertEquals(profile.id(), loaded.id());
        assertEquals(STUDENT_NO, loaded.studentNo());
        assertEquals(EMAIL, loaded.email());
        assertEquals("Alice", loaded.nickname());
        assertEquals(UserStatus.PENDING_VERIFICATION, loaded.status());
        assertEquals(Role.USER, loaded.role());
    }

    @Test
    @DisplayName("isActive, isMuted and isAdmin reflect the status and role of existing users")
    void predicatesForExistingUsers() {
        UserProfile active = registerAndVerifyAlice();
        UserProfile pending = service.register(
                new RegisterCommand("20230202", "bob@coplay.edu", PASSWORD, "Bob"));
        User admin = seedAdmin();

        assertTrue(service.isActive(active.id()));
        assertFalse(service.isMuted(active.id()));
        assertFalse(service.isAdmin(active.id()));

        assertFalse(service.isActive(pending.id()));
        assertFalse(service.isMuted(pending.id()));
        assertFalse(service.isAdmin(pending.id()));

        assertTrue(service.isActive(admin.getId()));
        assertFalse(service.isMuted(admin.getId()));
        assertTrue(service.isAdmin(admin.getId()));
    }

    @Test
    @DisplayName("all three predicates return false for an unknown user")
    void predicatesForUnknownUser() {
        assertFalse(service.isActive(UNKNOWN_USER_ID));
        assertFalse(service.isMuted(UNKNOWN_USER_ID));
        assertFalse(service.isAdmin(UNKNOWN_USER_ID));
    }
}
