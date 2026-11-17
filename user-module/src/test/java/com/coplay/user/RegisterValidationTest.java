package com.coplay.user;

import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration rules: required fields, email shape, password boundary, uniqueness and normalization.
 */
class RegisterValidationTest extends UserServiceTestSupport {

    @Test
    @DisplayName("register rejects a null student number without persisting or mailing")
    void rejectsNullStudentNo() {
        BizException exception = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(null, "bob@coplay.edu", PASSWORD, "Bob")));

        assertEquals(ErrorCode.INVALID_STUDENT_NO.getCode(), exception.getCode());
        assertEquals(ErrorCode.INVALID_STUDENT_NO.getMessage(), exception.getMessage());
        assertTrue(users.findByEmail("bob@coplay.edu").isEmpty());
        assertEquals(0, mail.sentCount());
    }

    @Test
    @DisplayName("register rejects a blank student number")
    void rejectsBlankStudentNo() {
        BizException exception = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand("   ", "bob@coplay.edu", PASSWORD, "Bob")));

        assertEquals(ErrorCode.INVALID_STUDENT_NO.getCode(), exception.getCode());
        assertEquals(0, mail.sentCount());
    }

    @Test
    @DisplayName("register rejects a null email and an email without @")
    void rejectsMissingAtSign() {
        BizException nullEmail = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, null, PASSWORD, "Alice")));
        assertEquals(ErrorCode.INVALID_EMAIL.getCode(), nullEmail.getCode());

        BizException noAtSign = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, "alice.coplay.edu", PASSWORD, "Alice")));
        assertEquals(ErrorCode.INVALID_EMAIL.getCode(), noAtSign.getCode());

        BizException blank = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, "   ", PASSWORD, "Alice")));
        assertEquals(ErrorCode.INVALID_EMAIL.getCode(), blank.getCode());

        assertEquals(0, mail.sentCount());
        assertTrue(users.findByEmail(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("register treats a null password as weak")
    void rejectsNullPassword() {
        BizException exception = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, EMAIL, null, "Alice")));

        assertEquals(ErrorCode.WEAK_PASSWORD.getCode(), exception.getCode());
        assertTrue(users.findByEmail(EMAIL).isEmpty());
        assertEquals(0, mail.sentCount());
    }

    @Test
    @DisplayName("password boundary: 7 characters is rejected, exactly 8 characters is accepted")
    void passwordLengthBoundary() {
        BizException sevenCharacters = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand("20230007", "seven@coplay.edu", "1234567", "Seven")));
        assertEquals(ErrorCode.WEAK_PASSWORD.getCode(), sevenCharacters.getCode());
        assertTrue(users.findByEmail("seven@coplay.edu").isEmpty());
        assertEquals(0, mail.sentCount());

        UserProfile accepted = service.register(
                new RegisterCommand("20230008", "eight@coplay.edu", "12345678", "Eight"));

        assertEquals(UserStatus.PENDING_VERIFICATION, accepted.status());
        assertEquals(1, mail.sentCount());
        assertTrue(hasher.matches("12345678", storedHash(accepted.id())));
    }

    @Test
    @DisplayName("register normalizes student number, email and nickname and then detects duplicates")
    void normalizesIdentityFields() {
        UserProfile profile = service.register(
                new RegisterCommand(" 20239999 ", "  Bob@Coplay.EDU ", PASSWORD, "  Bob  "));

        assertEquals("20239999", profile.studentNo());
        assertEquals("bob@coplay.edu", profile.email());
        assertEquals("Bob", profile.nickname());

        BizException duplicateStudentNo = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand("20239999", "other@coplay.edu", PASSWORD, "Other")));
        assertEquals(ErrorCode.DUPLICATE_STUDENT_NO.getCode(), duplicateStudentNo.getCode());

        BizException duplicateEmail = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand("20239998", "BOB@coplay.edu", PASSWORD, "Other")));
        assertEquals(ErrorCode.DUPLICATE_EMAIL.getCode(), duplicateEmail.getCode());
    }

    @Test
    @DisplayName("register falls back to the student number when the nickname is null, blank or padded")
    void fallsBackToStudentNumberAsNickname() {
        UserProfile nullNickname = service.register(
                new RegisterCommand("20230101", "null-nick@coplay.edu", PASSWORD, null));
        assertEquals("20230101", service.getProfile(nullNickname.id()).nickname());

        UserProfile blankNickname = service.register(
                new RegisterCommand("20230102", "blank-nick@coplay.edu", PASSWORD, "   "));
        assertEquals("20230102", service.getProfile(blankNickname.id()).nickname());

        UserProfile paddedNickname = service.register(
                new RegisterCommand("20230103", "padded-nick@coplay.edu", PASSWORD, "  Carol  "));
        assertEquals("Carol", service.getProfile(paddedNickname.id()).nickname());
    }

    @Test
    @DisplayName("register rejects a null command")
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> service.register(null));
    }
}
