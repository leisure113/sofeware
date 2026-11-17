package com.coplay.user;

import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.domain.VerificationCode;
import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.LoginResult;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.fake.FakeMailService;
import com.coplay.user.repository.InMemoryUserRepository;
import com.coplay.user.repository.InMemoryVerificationCodeRepository;
import com.coplay.user.service.UserServiceImpl;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;
import com.coplay.user.support.InMemoryTokenService;
import com.coplay.user.support.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String EMAIL = "alice@coplay.edu";
    private static final String STUDENT_NO = "20230001";
    private static final String PASSWORD = "alice12345";

    private InMemoryUserRepository users;
    private InMemoryVerificationCodeRepository codes;
    private FakeMailService mail;
    private InMemoryTokenService tokens;
    private Pbkdf2PasswordHasher hasher;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        codes = new InMemoryVerificationCodeRepository();
        mail = new FakeMailService();
        tokens = new InMemoryTokenService();
        hasher = new Pbkdf2PasswordHasher();
        service = new UserServiceImpl(users, codes, mail, hasher, tokens, CLOCK);
    }

    @Test
    @DisplayName("register creates a pending user, stores a 10-minute code and sends it by mail")
    void registerCreatesPendingUser() {
        UserProfile profile = service.register(new RegisterCommand(STUDENT_NO, EMAIL, PASSWORD, "Alice"));

        assertEquals(UserStatus.PENDING_VERIFICATION, profile.status());
        assertEquals(Role.USER, profile.role());
        assertEquals(STUDENT_NO, profile.studentNo());
        assertTrue(profile.id() > 0);
        assertFalse(service.isActive(profile.id()));

        assertEquals(1, mail.sentCount());
        String code = mail.lastCode(EMAIL);
        assertTrue(code.matches("\\d{6}"));

        VerificationCode stored = codes.findLatestByEmail(EMAIL).orElseThrow();
        assertEquals(code, stored.getCode());
        assertEquals(NOW.plusMinutes(10), stored.getExpiresAt());
        assertEquals(0, stored.getAttempts());
        assertEquals(5, stored.remainingAttempts());
    }

    @Test
    @DisplayName("register rejects a duplicate student number and a duplicate email")
    void registerRejectsDuplicates() {
        registerAlice();

        BizException duplicateStudentNo = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, "bob@coplay.edu", PASSWORD, "Bob")));
        assertEquals(ErrorCode.DUPLICATE_STUDENT_NO.getCode(), duplicateStudentNo.getCode());

        BizException duplicateEmail = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand("20230002", EMAIL, PASSWORD, "Alice 2")));
        assertEquals(ErrorCode.DUPLICATE_EMAIL.getCode(), duplicateEmail.getCode());
    }

    @Test
    @DisplayName("register rejects a password shorter than 8 characters")
    void registerRejectsWeakPassword() {
        BizException exception = assertThrows(BizException.class, () -> service.register(
                new RegisterCommand(STUDENT_NO, EMAIL, "1234567", "Alice")));

        assertEquals(ErrorCode.WEAK_PASSWORD.getCode(), exception.getCode());
        assertTrue(users.findByEmail(EMAIL).isEmpty());
        assertEquals(0, mail.sentCount());
    }

    @Test
    @DisplayName("send verification code issues a fresh code for an existing user")
    void sendVerificationCodeIssuesNewCode() {
        UserProfile profile = registerAlice();

        service.sendVerificationCode(EMAIL);

        assertEquals(2, mail.sentCount());
        String code = mail.lastCode(EMAIL);
        assertTrue(code.matches("\\d{6}"));
        VerificationCode stored = codes.findLatestByEmail(EMAIL).orElseThrow();
        assertEquals(code, stored.getCode());
        assertEquals(NOW.plusMinutes(10), stored.getExpiresAt());

        UserProfile verified = service.verifyCampus(new VerifyCampusCommand(EMAIL, code));
        assertEquals(UserStatus.ACTIVE, verified.status());
        assertEquals(profile.id(), verified.id());
    }

    @Test
    @DisplayName("verify campus with the mailed code activates the user and consumes the code")
    void verifyCampusActivatesUser() {
        UserProfile profile = registerAlice();

        UserProfile verified = service.verifyCampus(
                new VerifyCampusCommand(EMAIL, mail.lastCode(EMAIL)));

        assertEquals(UserStatus.ACTIVE, verified.status());
        assertTrue(service.isActive(profile.id()));
        assertEquals(UserStatus.ACTIVE, service.getProfile(profile.id()).status());
        assertTrue(codes.findLatestByEmail(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("wrong code increments attempts, is rejected and does not activate the user")
    void wrongCodeIncrementsAttempts() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);

        BizException exception = assertThrows(BizException.class, () -> service.verifyCampus(
                new VerifyCampusCommand(EMAIL, wrongCode(code))));

        assertEquals(ErrorCode.INVALID_CODE.getCode(), exception.getCode());
        VerificationCode stored = codes.findLatestByEmail(EMAIL).orElseThrow();
        assertEquals(1, stored.getAttempts());
        assertEquals(4, stored.remainingAttempts());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());

        UserProfile verified = service.verifyCampus(new VerifyCampusCommand(EMAIL, code));
        assertEquals(UserStatus.ACTIVE, verified.status());
    }

    @Test
    @DisplayName("expired verification code is rejected")
    void expiredCodeIsRejected() {
        UserProfile profile = registerAlice();
        String code = mail.lastCode(EMAIL);
        UserServiceImpl laterService = new UserServiceImpl(
                users, codes, mail, hasher, tokens, Clock.offset(CLOCK, Duration.ofMinutes(11)));

        BizException exception = assertThrows(BizException.class, () -> laterService.verifyCampus(
                new VerifyCampusCommand(EMAIL, code)));

        assertEquals(ErrorCode.CODE_EXPIRED.getCode(), exception.getCode());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("login returns a token that resolves to the active user")
    void loginReturnsToken() {
        UserProfile profile = registerAlice();
        verifyAlice();

        LoginResult result = service.login(new LoginCommand(EMAIL, PASSWORD));

        assertFalse(result.token().isBlank());
        assertEquals(profile.id(), result.profile().id());
        assertEquals(UserStatus.ACTIVE, result.profile().status());
        assertEquals(profile.id(), tokens.verify(result.token()));
    }

    @Test
    @DisplayName("login rejects a user that is still pending verification")
    void loginRejectsInactiveUser() {
        UserProfile profile = registerAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, PASSWORD)));

        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), exception.getCode());
        assertEquals(UserStatus.PENDING_VERIFICATION, service.getProfile(profile.id()).status());
    }

    @Test
    @DisplayName("login rejects a wrong password")
    void loginRejectsWrongPassword() {
        registerAlice();
        verifyAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.login(new LoginCommand(EMAIL, "wrong-password")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("admin can mute and ban a user, and isAdmin reflects the role")
    void adminCanChangeStatus() {
        UserProfile profile = registerAlice();
        verifyAlice();
        User admin = seedAdmin();

        service.changeStatus(admin.getId(), profile.id(), UserStatus.MUTED);

        assertTrue(service.isMuted(profile.id()));
        assertFalse(service.isActive(profile.id()));
        assertEquals(UserStatus.MUTED, service.getProfile(profile.id()).status());

        service.changeStatus(admin.getId(), profile.id(), UserStatus.BANNED);

        assertEquals(UserStatus.BANNED, service.getProfile(profile.id()).status());
        assertTrue(service.isAdmin(admin.getId()));
        assertFalse(service.isAdmin(profile.id()));
    }

    @Test
    @DisplayName("non-admin cannot change a user status")
    void nonAdminCannotChangeStatus() {
        UserProfile profile = registerAlice();
        verifyAlice();

        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(profile.id(), profile.id(), UserStatus.BANNED));

        assertEquals(ErrorCode.NOT_ADMIN.getCode(), exception.getCode());
        assertEquals(UserStatus.ACTIVE, service.getProfile(profile.id()).status());
    }

    private UserProfile registerAlice() {
        return service.register(new RegisterCommand(STUDENT_NO, EMAIL, PASSWORD, "Alice"));
    }

    private void verifyAlice() {
        service.verifyCampus(new VerifyCampusCommand(EMAIL, mail.lastCode(EMAIL)));
    }

    private User seedAdmin() {
        User admin = new User(
                9001L,
                "ADMIN001",
                "admin@coplay.edu",
                hasher.hash("admin12345"),
                "Campus Admin",
                UserStatus.ACTIVE,
                Role.ADMIN);
        return users.save(admin);
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}
