package com.coplay.user;

import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
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
 * Administrative status changes: authorization, unknown users, every status and self-changes.
 */
class ChangeStatusTest extends UserServiceTestSupport {

    @Test
    @DisplayName("an admin can apply every status to a user")
    void adminAppliesEveryStatus() {
        UserProfile profile = registerAndVerifyAlice();
        User admin = seedAdmin();
        assertTrue(service.isActive(profile.id()));
        assertFalse(service.isMuted(profile.id()));

        service.changeStatus(admin.getId(), profile.id(), UserStatus.PENDING_VERIFICATION);
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
        assertFalse(service.isActive(profile.id()));

        service.changeStatus(admin.getId(), profile.id(), UserStatus.ACTIVE);
        assertTrue(service.isActive(profile.id()));
        assertFalse(service.isMuted(profile.id()));

        service.changeStatus(admin.getId(), profile.id(), UserStatus.MUTED);
        assertTrue(service.isMuted(profile.id()));
        assertFalse(service.isActive(profile.id()));
        assertEquals(UserStatus.MUTED, service.getProfile(profile.id()).status());

        service.changeStatus(admin.getId(), profile.id(), UserStatus.BANNED);
        assertEquals(UserStatus.BANNED, service.getProfile(profile.id()).status());
        assertFalse(service.isActive(profile.id()));
        assertFalse(service.isMuted(profile.id()));
    }

    @Test
    @DisplayName("an unknown operator is rejected before the target is touched")
    void rejectsUnknownOperator() {
        UserProfile profile = registerAndVerifyAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(UNKNOWN_USER_ID, profile.id(), UserStatus.BANNED));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(UserStatus.ACTIVE, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("an admin changing an unknown target gets USER_NOT_FOUND")
    void rejectsUnknownTarget() {
        User admin = seedAdmin();

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(admin.getId(), UNKNOWN_USER_ID, UserStatus.BANNED));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
        assertTrue(service.isAdmin(admin.getId()));
        assertEquals(UserStatus.ACTIVE, service.getProfile(admin.getId()).status());
    }

    @Test
    @DisplayName("a normal user cannot change the status of another user")
    void rejectsNonAdminOperator() {
        UserProfile alice = registerAndVerifyAlice();
        User admin = seedAdmin();

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(alice.id(), admin.getId(), UserStatus.BANNED));

        assertEquals(ErrorCode.NOT_ADMIN.getCode(), exception.getCode());
        assertTrue(service.isAdmin(admin.getId()));
        assertEquals(UserStatus.ACTIVE, service.getProfile(admin.getId()).status());
    }

    @Test
    @DisplayName("a normal user cannot change their own status")
    void rejectsNonAdminChangingSelf() {
        UserProfile alice = registerAndVerifyAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(alice.id(), alice.id(), UserStatus.MUTED));

        assertEquals(ErrorCode.NOT_ADMIN.getCode(), exception.getCode());
        assertEquals(UserStatus.ACTIVE, service.getProfile(alice.id()).status());
    }

    @Test
    @DisplayName("an admin may change their own status and restore it because the role is unchanged")
    void adminCanChangeOwnStatus() {
        User admin = seedAdmin();

        service.changeStatus(admin.getId(), admin.getId(), UserStatus.MUTED);

        assertTrue(service.isMuted(admin.getId()));
        assertTrue(service.isAdmin(admin.getId()));
        assertEquals(UserStatus.MUTED, service.getProfile(admin.getId()).status());

        service.changeStatus(admin.getId(), admin.getId(), UserStatus.ACTIVE);

        assertTrue(service.isActive(admin.getId()));
        assertTrue(service.isAdmin(admin.getId()));
    }

    @Test
    @DisplayName("changeStatus rejects a null status without changing the user")
    void rejectsNullStatus() {
        UserProfile profile = registerAndVerifyAlice();
        User admin = seedAdmin();

        assertThrows(NullPointerException.class,
                () -> service.changeStatus(admin.getId(), profile.id(), null));

        assertEquals(UserStatus.ACTIVE, service.getProfile(profile.id()).status());
    }
}
