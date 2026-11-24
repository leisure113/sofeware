package com.coplay.integration.support;

import com.coplay.content.api.ContentService;
import com.coplay.content.domain.Board;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.repository.InMemoryContentRepository;
import com.coplay.content.service.ContentServiceImpl;
import com.coplay.governance.api.GovernanceService;
import com.coplay.governance.repository.InMemoryAdminLogRepository;
import com.coplay.governance.repository.InMemoryReportRepository;
import com.coplay.governance.service.GovernanceServiceImpl;
import com.coplay.team.api.TeamService;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamDetail;
import com.coplay.team.repository.InMemoryTeamRepository;
import com.coplay.team.service.TeamServiceImpl;
import com.coplay.team.support.InMemoryTeamLockRegistry;
import com.coplay.user.api.UserService;
import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;
import com.coplay.user.repository.InMemoryUserRepository;
import com.coplay.user.repository.InMemoryVerificationCodeRepository;
import com.coplay.user.service.UserServiceImpl;
import com.coplay.user.support.InMemoryTokenService;
import com.coplay.user.support.Pbkdf2PasswordHasher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Composition root for cross-module integration tests.
 * It wires the real service implementations and adapts every cross-module port.
 */
public final class IntegrationFixture {

    public static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    public static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    public final InMemoryUserRepository userRepository = new InMemoryUserRepository();
    public final InMemoryVerificationCodeRepository codeRepository = new InMemoryVerificationCodeRepository();
    public final CapturingMailService mail = new CapturingMailService();
    public final NotificationAdapter notifications = new NotificationAdapter();
    public final InMemoryContentRepository contentRepository = new InMemoryContentRepository();
    public final InMemoryTeamRepository teamRepository = new InMemoryTeamRepository();
    public final InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    public final InMemoryAdminLogRepository adminLogRepository = new InMemoryAdminLogRepository();

    public final UserService userService;
    public final ContentService contentService;
    public final TeamService teamService;
    public final GovernanceService governanceService;

    public final UserQueryAdapter userQuery;
    public final AdminQueryAdapter adminQuery;
    public final ContentAdminAdapter contentAdmin;
    public final TeamAdminAdapter teamAdmin;
    public final UserAdminAdapter userAdmin;

    public final long adminId;

    public IntegrationFixture() {
        userService = new UserServiceImpl(
                userRepository,
                codeRepository,
                mail,
                new Pbkdf2PasswordHasher(),
                new InMemoryTokenService(),
                CLOCK);
        adminId = seedAdmin();
        userQuery = new UserQueryAdapter(userService);
        adminQuery = new AdminQueryAdapter(userService);
        GameQueryAdapter gameQuery = new GameQueryAdapter(Set.of(100L, 200L));

        contentRepository.saveBoard(new Board(1L, 100L, "General"));
        contentRepository.saveBoard(new Board(2L, 200L, "Second game"));
        contentService = new ContentServiceImpl(
                contentRepository,
                userQuery,
                gameQuery,
                new SensitiveWordAdapter(Set.of("badword")),
                notifications,
                CLOCK);

        teamService = new TeamServiceImpl(
                teamRepository,
                userQuery,
                gameQuery,
                notifications,
                new InMemoryTeamLockRegistry(),
                CLOCK);

        contentAdmin = new ContentAdminAdapter(contentService);
        teamAdmin = new TeamAdminAdapter(teamService);
        userAdmin = new UserAdminAdapter(userService, adminId);
        governanceService = new GovernanceServiceImpl(
                reportRepository,
                adminLogRepository,
                adminQuery,
                userQuery,
                contentAdmin,
                userAdmin,
                teamAdmin,
                notifications,
                CLOCK);
    }

    public long registerActive(String suffix) {
        String email = "user" + suffix + "@coplay.edu";
        UserProfile profile = userService.register(new RegisterCommand(
                "S" + suffix, email, "password123", "User" + suffix));
        userService.verifyCampus(new VerifyCampusCommand(email, mail.latestCode(email)));
        return profile.id();
    }

    public long registerPending(String suffix) {
        String email = "pending" + suffix + "@coplay.edu";
        UserProfile profile = userService.register(new RegisterCommand(
                "P" + suffix, email, "password123", "Pending" + suffix));
        return profile.id();
    }

    public void mute(long userId) {
        userService.changeStatus(adminId, userId, UserStatus.MUTED);
    }

    public long createPost(long authorId) {
        PostDetail post = contentService.createPost(
                new CreatePostCommand(1L, authorId, "Integration post", "Cross-module content"));
        return post.postId();
    }

    public long createComment(long postId, long authorId) {
        return contentService.addComment(new CreateCommentCommand(postId, authorId, "Integration comment"))
                .commentId();
    }

    public TeamDetail createTeam(long leaderId) {
        TeamDetail team = teamService.create(new CreateTeamCommand(
                leaderId, 100L, "Integration team", "Gold", "Platinum",
                NOW.plusHours(2), 120, 3, "REQUIRED", "PC", "ROOM-INTEGRATION"));
        teamAdmin.registerCaptain(team.teamId(), leaderId);
        return team;
    }

    public User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
    }

    public long countAdminLogs() {
        return adminLogRepository.findAll().size();
    }

    private long seedAdmin() {
        User admin = new User(
                userRepository.nextId(),
                "ADMIN",
                "admin@coplay.edu",
                new Pbkdf2PasswordHasher().hash("admin1234"),
                "Administrator",
                UserStatus.ACTIVE,
                Role.ADMIN);
        userRepository.save(admin);
        return admin.getId();
    }
}
