package com.coplay.user;

import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.domain.VerificationCode;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.fake.FakeMailService;
import com.coplay.user.repository.InMemoryUserRepository;
import com.coplay.user.repository.InMemoryVerificationCodeRepository;
import com.coplay.user.service.UserServiceImpl;
import com.coplay.user.support.InMemoryTokenService;
import com.coplay.user.support.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Shared fixture for service-level tests: in-memory adapters, fixed clock and a fresh service per test.
 * Tests stay deterministic because the clock never moves unless a test asks for an offset clock.
 */
abstract class UserServiceTestSupport {

    protected static final Instant FIXED_INSTANT = Instant.parse("2026-11-17T10:00:00Z");
    protected static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    protected static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    protected static final String EMAIL = "alice@coplay.edu";
    protected static final String STUDENT_NO = "20230001";
    protected static final String PASSWORD = "alice12345";
    protected static final String NEW_PASSWORD = "newpass123";
    protected static final long UNKNOWN_USER_ID = 424242L;

    protected InMemoryUserRepository users;
    protected InMemoryVerificationCodeRepository codes;
    protected FakeMailService mail;
    protected InMemoryTokenService tokens;
    protected Pbkdf2PasswordHasher hasher;
    protected UserServiceImpl service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        codes = new InMemoryVerificationCodeRepository();
        mail = new FakeMailService();
        tokens = new InMemoryTokenService();
        hasher = new Pbkdf2PasswordHasher();
        service = new UserServiceImpl(users, codes, mail, hasher, tokens, CLOCK);
    }

    protected UserProfile registerAlice() {
        return service.register(new RegisterCommand(STUDENT_NO, EMAIL, PASSWORD, "Alice"));
    }

    protected void verifyAlice() {
        service.verifyCampus(new VerifyCampusCommand(EMAIL, mail.lastCode(EMAIL)));
    }

    /**
     * Registers and verifies Alice so she is an ACTIVE user with a known password.
     */
    protected UserProfile registerAndVerifyAlice() {
        UserProfile profile = registerAlice();
        verifyAlice();
        return profile;
    }

    protected User seedAdmin() {
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

    /**
     * A service sharing the same repositories but seeing a clock moved by {@code duration}.
     */
    protected UserServiceImpl serviceAt(Duration duration) {
        return new UserServiceImpl(users, codes, mail, hasher, tokens, Clock.offset(CLOCK, duration));
    }

    protected String storedHash(long userId) {
        return users.findById(userId).orElseThrow().getPasswordHash();
    }

    protected VerificationCode storedCode() {
        return codes.findLatestByEmail(EMAIL).orElseThrow();
    }

    protected static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    protected static String wrongCodeOtherThan(String code) {
        return code.equals("123456") ? "654321" : "123456";
    }
}
