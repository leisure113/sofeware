package com.coplay.user.service;

import com.coplay.user.api.MailService;
import com.coplay.user.api.PasswordHasher;
import com.coplay.user.api.TokenService;
import com.coplay.user.api.UserService;
import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.domain.VerificationCode;
import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.LoginResult;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.repository.UserRepository;
import com.coplay.user.repository.VerificationCodeRepository;
import com.coplay.user.support.BizException;
import com.coplay.user.support.ErrorCode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * User module implementation.
 *
 * <p>The service only depends on ports (repositories, mail, password hasher, token service and clock),
 * so it can be tested and run without Spring, MySQL or external services.</p>
 */
public final class UserServiceImpl implements UserService {

    public static final int CODE_TTL_MINUTES = 10;
    private static final int CODE_BOUND = 1_000_000;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final MailService mailService;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserServiceImpl(UserRepository userRepository,
                           VerificationCodeRepository codeRepository,
                           MailService mailService,
                           PasswordHasher passwordHasher,
                           TokenService tokenService,
                           Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.codeRepository = Objects.requireNonNull(codeRepository, "codeRepository");
        this.mailService = Objects.requireNonNull(mailService, "mailService");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UserProfile register(RegisterCommand command) {
        Objects.requireNonNull(command, "command");
        String studentNo = normalizeStudentNo(command.studentNo());
        String email = normalizeEmail(command.email());
        validatePassword(command.password());

        if (userRepository.findByStudentNo(studentNo).isPresent()) {
            throw new BizException(ErrorCode.DUPLICATE_STUDENT_NO);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BizException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = new User(
                userRepository.nextId(),
                studentNo,
                email,
                passwordHasher.hash(command.password()),
                normalizeNickname(command.nickname(), studentNo),
                UserStatus.PENDING_VERIFICATION,
                Role.USER);
        userRepository.save(user);
        issueCode(user);
        return toProfile(user);
    }

    @Override
    public void sendVerificationCode(String email) {
        User user = userRepository.findByEmail(normalizeLookupEmail(email))
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        issueCode(user);
    }

    @Override
    public UserProfile verifyCampus(VerifyCampusCommand command) {
        Objects.requireNonNull(command, "command");
        User user = userRepository.findByEmail(normalizeLookupEmail(command.email()))
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        VerificationCode verification = codeRepository.findLatestByEmail(user.getEmail())
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_CODE));

        if (verification.isExpired(now())) {
            throw new BizException(ErrorCode.CODE_EXPIRED);
        }
        if (verification.getAttempts() >= VerificationCode.MAX_ATTEMPTS) {
            throw new BizException(ErrorCode.TOO_MANY_ATTEMPTS);
        }
        if (!verification.matches(command.code())) {
            verification.incrementAttempts();
            codeRepository.save(verification);
            throw new BizException(ErrorCode.INVALID_CODE, verification.remainingAttempts() + " attempts left");
        }

        user.activate();
        userRepository.save(user);
        codeRepository.deleteByEmail(user.getEmail());
        return toProfile(user);
    }

    @Override
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        User user = userRepository.findByEmail(normalizeLookupEmail(command.email()))
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.isActive()) {
            throw new BizException(ErrorCode.USER_NOT_ACTIVE);
        }
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        String token = tokenService.issue(user.getId());
        return new LoginResult(token, toProfile(user));
    }

    @Override
    public UserProfile getProfile(long userId) {
        return userRepository.findById(userId)
                .map(this::toProfile)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public void changeStatus(long operatorId, long userId, UserStatus status) {
        Objects.requireNonNull(status, "status");
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (!operator.isAdmin()) {
            throw new BizException(ErrorCode.NOT_ADMIN);
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        target.changeStatus(status);
        userRepository.save(target);
    }

    @Override
    public boolean isActive(long userId) {
        return userRepository.findById(userId).map(User::isActive).orElse(false);
    }

    @Override
    public boolean isMuted(long userId) {
        return userRepository.findById(userId).map(User::isMuted).orElse(false);
    }

    @Override
    public boolean isAdmin(long userId) {
        return userRepository.findById(userId).map(User::isAdmin).orElse(false);
    }

    private void issueCode(User user) {
        String code = generateCode();
        VerificationCode verification = new VerificationCode(
                user.getId(),
                user.getEmail(),
                code,
                now().plusMinutes(CODE_TTL_MINUTES));
        codeRepository.save(verification);
        mailService.sendVerificationCode(user.getEmail(), code);
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(CODE_BOUND));
    }

    private String normalizeStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            throw new BizException(ErrorCode.INVALID_STUDENT_NO);
        }
        return studentNo.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BizException(ErrorCode.INVALID_EMAIL);
        }
        String trimmed = email.trim();
        if (!trimmed.contains("@")) {
            throw new BizException(ErrorCode.INVALID_EMAIL);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeLookupEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.WEAK_PASSWORD);
        }
    }

    private String normalizeNickname(String nickname, String fallback) {
        return nickname == null || nickname.isBlank() ? fallback : nickname.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getStudentNo(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus(),
                user.getRole());
    }
}
