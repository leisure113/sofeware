package com.coplay.user;

import com.coplay.user.api.MailService;
import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.LoginResult;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.repository.InMemoryUserRepository;
import com.coplay.user.repository.InMemoryVerificationCodeRepository;
import com.coplay.user.service.UserServiceImpl;
import com.coplay.user.support.BizException;
import com.coplay.user.support.InMemoryTokenService;
import com.coplay.user.support.Pbkdf2PasswordHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standalone demo: register -> verify campus -> login -> admin moderation -> error cases.
 * Run with: java -jar target/user-module-1.0.0.jar
 */
public final class UserModuleDemo {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);

    private UserModuleDemo() {
    }

    public static void main(String[] args) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryVerificationCodeRepository codes = new InMemoryVerificationCodeRepository();
        DemoMailService mail = new DemoMailService();
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();
        InMemoryTokenService tokens = new InMemoryTokenService();
        UserServiceImpl service = new UserServiceImpl(users, codes, mail, hasher, tokens, CLOCK);

        User admin = new User(
                9001L,
                "ADMIN001",
                "admin@coplay.edu",
                hasher.hash("admin12345"),
                "Campus Admin",
                UserStatus.ACTIVE,
                Role.ADMIN);
        users.save(admin);

        System.out.println("CoPlay user module demo");
        System.out.println("=======================");

        UserProfile registered = service.register(
                new RegisterCommand("20230001", "alice@coplay.edu", "alice12345", "Alice"));
        System.out.println("Registered: id=" + registered.id()
                + ", studentNo=" + registered.studentNo()
                + ", status=" + registered.status());

        String code = mail.lastCode(registered.email());
        System.out.println("Verification code sent to " + registered.email()
                + ": " + code + " (valid for " + UserServiceImpl.CODE_TTL_MINUTES + " minutes)");

        try {
            service.verifyCampus(new VerifyCampusCommand(registered.email(), wrongCode(code)));
        } catch (BizException exception) {
            System.out.println("Wrong code rejected: " + exception.getCode() + " / " + exception.getMessage());
        }

        UserProfile verified = service.verifyCampus(new VerifyCampusCommand(registered.email(), code));
        System.out.println("Campus verified: status=" + verified.status());

        LoginResult login = service.login(new LoginCommand(registered.email(), "alice12345"));
        System.out.println("Login token: " + login.token());
        System.out.println("Token resolves to userId=" + tokens.verify(login.token()));

        service.changeStatus(admin.getId(), registered.id(), UserStatus.MUTED);
        System.out.println("Admin muted user: isActive=" + service.isActive(registered.id())
                + ", isMuted=" + service.isMuted(registered.id()));

        try {
            service.login(new LoginCommand(registered.email(), "alice12345"));
        } catch (BizException exception) {
            System.out.println("Muted user login rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        service.changeStatus(admin.getId(), registered.id(), UserStatus.ACTIVE);
        System.out.println("Admin restored user: isActive=" + service.isActive(registered.id()));

        try {
            service.register(new RegisterCommand("20230001", "alice2@coplay.edu", "alice12345", "Alice 2"));
        } catch (BizException exception) {
            System.out.println("Duplicate student number rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            service.register(new RegisterCommand("20230002", "alice@coplay.edu", "alice12345", "Alice 3"));
        } catch (BizException exception) {
            System.out.println("Duplicate email rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            service.register(new RegisterCommand("20230003", "bob@coplay.edu", "short", "Bob"));
        } catch (BizException exception) {
            System.out.println("Weak password rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            service.changeStatus(registered.id(), registered.id(), UserStatus.BANNED);
        } catch (BizException exception) {
            System.out.println("Non-admin change rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        service.sendVerificationCode(registered.email());
        String resentCode = mail.lastCode(registered.email());
        UserServiceImpl laterService = new UserServiceImpl(
                users, codes, mail, hasher, tokens, Clock.offset(CLOCK, Duration.ofMinutes(11)));
        try {
            laterService.verifyCampus(new VerifyCampusCommand(registered.email(), resentCode));
        } catch (BizException exception) {
            System.out.println("Expired code rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            tokens.verify("not-a-real-token");
        } catch (BizException exception) {
            System.out.println("Unknown token rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        System.out.println("Mail outbox:");
        mail.mails().forEach(item ->
                System.out.println("  - to=" + item.email() + ", code=" + item.code()));
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static final class DemoMailService implements MailService {

        private final List<Mail> mails = new CopyOnWriteArrayList<>();

        @Override
        public void sendVerificationCode(String email, String code) {
            mails.add(new Mail(email, code));
        }

        String lastCode(String email) {
            return mails.stream()
                    .filter(mail -> mail.email().equals(email))
                    .reduce((first, second) -> second)
                    .map(Mail::code)
                    .orElseThrow(() -> new IllegalStateException("No mail sent to " + email));
        }

        List<Mail> mails() {
            return List.copyOf(mails);
        }

        private record Mail(String email, String code) {
        }
    }
}
