package com.coplay.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User aggregate: status transitions, role predicates and password hash replacement.
 */
class UserTest {

    private static final long ID = 1001L;
    private static final String STUDENT_NO = "20230001";
    private static final String EMAIL = "alice@coplay.edu";
    private static final String HASH = "pbkdf2-sha256$120000$c2FsdA==$aGFzaA==";
    private static final String NICKNAME = "Alice";

    @Test
    @DisplayName("getters expose the stored fields")
    void gettersExposeStoredFields() {
        User user = user(UserStatus.PENDING_VERIFICATION, Role.USER);

        assertEquals(ID, user.getId());
        assertEquals(STUDENT_NO, user.getStudentNo());
        assertEquals(EMAIL, user.getEmail());
        assertEquals(HASH, user.getPasswordHash());
        assertEquals(NICKNAME, user.getNickname());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    @DisplayName("activate moves any account to ACTIVE")
    void activateMovesToActive() {
        User user = user(UserStatus.PENDING_VERIFICATION, Role.USER);

        user.activate();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isActive());
        assertFalse(user.isMuted());
    }

    @Test
    @DisplayName("changeStatus applies MUTED, BANNED and ACTIVE to the predicates")
    void changeStatusAppliesPredicates() {
        User user = user(UserStatus.PENDING_VERIFICATION, Role.USER);

        user.changeStatus(UserStatus.MUTED);
        assertEquals(UserStatus.MUTED, user.getStatus());
        assertTrue(user.isMuted());
        assertFalse(user.isActive());

        user.changeStatus(UserStatus.BANNED);
        assertFalse(user.isActive());
        assertFalse(user.isMuted());

        user.changeStatus(UserStatus.ACTIVE);
        assertTrue(user.isActive());
        assertFalse(user.isMuted());
    }

    @Test
    @DisplayName("changeStatus rejects a null status and keeps the current one")
    void changeStatusRejectsNull() {
        User user = user(UserStatus.ACTIVE, Role.USER);

        assertThrows(NullPointerException.class, () -> user.changeStatus(null));

        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("isAdmin reflects the role only")
    void isAdminReflectsRole() {
        assertFalse(user(UserStatus.ACTIVE, Role.USER).isAdmin());
        assertTrue(user(UserStatus.ACTIVE, Role.ADMIN).isAdmin());
        assertTrue(user(UserStatus.BANNED, Role.ADMIN).isAdmin());
    }

    @Test
    @DisplayName("changePassword replaces the hash and rejects null")
    void changePasswordReplacesHash() {
        User user = user(UserStatus.ACTIVE, Role.USER);

        user.changePassword("pbkdf2-sha256$1000$bmV3c2FsdA==$bmV3aGFzaA==");
        assertEquals("pbkdf2-sha256$1000$bmV3c2FsdA==$bmV3aGFzaA==", user.getPasswordHash());

        assertThrows(NullPointerException.class, () -> user.changePassword(null));
        assertEquals("pbkdf2-sha256$1000$bmV3c2FsdA==$bmV3aGFzaA==", user.getPasswordHash());
    }

    @Test
    @DisplayName("the constructor rejects null required fields")
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> new User(ID, null, EMAIL, HASH, NICKNAME, UserStatus.ACTIVE, Role.USER));
        assertThrows(NullPointerException.class,
                () -> new User(ID, STUDENT_NO, null, HASH, NICKNAME, UserStatus.ACTIVE, Role.USER));
        assertThrows(NullPointerException.class,
                () -> new User(ID, STUDENT_NO, EMAIL, null, NICKNAME, UserStatus.ACTIVE, Role.USER));
        assertThrows(NullPointerException.class,
                () -> new User(ID, STUDENT_NO, EMAIL, HASH, null, UserStatus.ACTIVE, Role.USER));
        assertThrows(NullPointerException.class,
                () -> new User(ID, STUDENT_NO, EMAIL, HASH, NICKNAME, null, Role.USER));
        assertThrows(NullPointerException.class,
                () -> new User(ID, STUDENT_NO, EMAIL, HASH, NICKNAME, UserStatus.ACTIVE, null));
    }

    private static User user(UserStatus status, Role role) {
        return new User(ID, STUDENT_NO, EMAIL, HASH, NICKNAME, status, role);
    }
}
