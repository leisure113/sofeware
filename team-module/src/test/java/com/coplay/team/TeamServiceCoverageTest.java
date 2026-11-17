package com.coplay.team;

import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.TeamApplication;
import com.coplay.team.domain.TeamStatus;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamBrief;
import com.coplay.team.dto.TeamDetail;
import com.coplay.team.fake.FakeGameQueryService;
import com.coplay.team.fake.FakeNotificationService;
import com.coplay.team.fake.FakeUserQueryService;
import com.coplay.team.repository.InMemoryTeamRepository;
import com.coplay.team.service.TeamServiceImpl;
import com.coplay.team.support.BizException;
import com.coplay.team.support.ErrorCode;
import com.coplay.team.support.InMemoryTeamLockRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamServiceCoverageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private InMemoryTeamRepository repository;
    private FakeUserQueryService users;
    private FakeGameQueryService games;
    private FakeNotificationService notifications;
    private TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTeamRepository();
        users = new FakeUserQueryService();
        games = new FakeGameQueryService();
        notifications = new FakeNotificationService();
        service = new TeamServiceImpl(
                repository, users, games, notifications,
                new InMemoryTeamLockRegistry(), CLOCK);
    }

    @Test
    @DisplayName("create rejects a disabled game")
    void createRejectsDisabledGame() {
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command(999L, 3, "Weekend team")));
        assertEquals(ErrorCode.GAME_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create rejects a title that is too short")
    void createRejectsShortTitle() {
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command(100L, 3, "abc")));
        assertEquals(ErrorCode.INVALID_TITLE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create rejects invalid duration")
    void createRejectsInvalidDuration() {
        CreateTeamCommand command = new CreateTeamCommand(
                1L, 100L, "Weekend team", "Gold", "Platinum",
                NOW.plusHours(2), 10, 3, "REQUIRED", "PC", "ROOM-1");
        BizException exception = assertThrows(BizException.class, () -> service.create(command));
        assertEquals(ErrorCode.INVALID_DURATION.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create rejects invalid voice requirement")
    void createRejectsInvalidVoice() {
        CreateTeamCommand command = new CreateTeamCommand(
                1L, 100L, "Weekend team", "Gold", "Platinum",
                NOW.plusHours(2), 120, 3, "SOMETIMES", "PC", "ROOM-1");
        BizException exception = assertThrows(BizException.class, () -> service.create(command));
        assertEquals(ErrorCode.INVALID_VOICE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("listByGame returns only the requested game and maps TeamBrief")
    void listByGameReturnsRequestedGame() {
        TeamDetail team100 = service.create(command(100L, 3, "Game 100 team"));
        games.enable(101L);
        service.create(command(101L, 3, "Game 101 team"));

        List<TeamBrief> result = service.listByGame(100L);

        assertEquals(1, result.size());
        assertEquals(team100.teamId(), result.get(0).teamId());
        assertEquals(100L, result.get(0).gameId());
        assertEquals("Game 100 team", result.get(0).title());
        assertEquals(0, result.get(0).approvedCount());
        assertEquals(TeamStatus.RECRUITING, result.get(0).status());
    }

    @Test
    @DisplayName("listByGame filters a team that expires before the listing")
    void listByGameFiltersExpiredTeam() {
        TeamDetail team = service.create(command(100L, 3, "Soon expired", NOW.plusHours(1)));
        TeamServiceImpl later = new TeamServiceImpl(
                repository, users, games, notifications,
                new InMemoryTeamLockRegistry(), Clock.offset(CLOCK, Duration.ofHours(2)));

        assertTrue(later.listByGame(100L).isEmpty());
        assertEquals(TeamStatus.EXPIRED, later.get(team.teamId(), 1L).status());
    }

    @Test
    @DisplayName("get rejects an unknown team")
    void getRejectsUnknownTeam() {
        BizException exception = assertThrows(BizException.class, () -> service.get(999L, 1L));
        assertEquals(ErrorCode.TEAM_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply rejects an unknown team")
    void applyRejectsUnknownTeam() {
        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(999L, 2L, "hello")));
        assertEquals(ErrorCode.TEAM_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply rejects an inactive user")
    void applyRejectsInactiveUser() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 9L, "hello")));
        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply rejects a closed team")
    void applyRejectsClosedTeam() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));
        service.close(team.teamId(), 1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 2L, "hello")));
        assertEquals(ErrorCode.TEAM_CLOSED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply repairs FULL status when approved count already reached capacity")
    void applyRepairsFullStatusWhenApprovedCountAtCapacity() {
        TeamDetail team = service.create(command(100L, 2, "Weekend team"));
        seedApplication(team.teamId(), 2L, ApplicationStatus.APPROVED);

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 3L, "hello")));
        assertEquals(ErrorCode.TEAM_FULL.getCode(), exception.getCode());
        assertEquals(TeamStatus.FULL, service.get(team.teamId(), 1L).status());
    }

    @Test
    @DisplayName("approve rejects unknown application")
    void approveRejectsUnknownApplication() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(new ApproveCommand(team.teamId(), 999L, 1L, true)));
        assertEquals(ErrorCode.APPLICATION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("approve rejects an already handled application")
    void approveRejectsHandledApplication() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "hello"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true)));
        assertEquals(ErrorCode.APPLICATION_NOT_PENDING.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("approve rejects an application for a closed team")
    void approveRejectsClosedTeam() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "hello"));
        service.close(team.teamId(), 1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true)));
        assertEquals(ErrorCode.TEAM_CLOSED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("approve repairs FULL status when approved count is already at capacity")
    void approveRepairsFullStatusWhenApprovedCountAtCapacity() {
        TeamDetail team = service.create(command(100L, 2, "Weekend team"));
        seedApplication(team.teamId(), 2L, ApplicationStatus.APPROVED);
        TeamApplication pending = seedApplication(team.teamId(), 3L, ApplicationStatus.PENDING);

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(new ApproveCommand(team.teamId(), pending.getApplicationId(), 1L, true)));
        assertEquals(ErrorCode.TEAM_FULL.getCode(), exception.getCode());
        assertEquals(TeamStatus.FULL, service.get(team.teamId(), 1L).status());
    }

    @Test
    @DisplayName("close rejects an already closed team")
    void closeRejectsAlreadyClosedTeam() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));
        service.close(team.teamId(), 1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.close(team.teamId(), 1L));
        assertEquals(ErrorCode.TEAM_CLOSED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("close notifies approved members")
    void closeNotifiesApprovedMembers() {
        TeamDetail team = service.create(command(100L, 2, "Weekend team"));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "hello"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        service.close(team.teamId(), 1L);

        assertTrue(notifications.notifications().stream()
                .anyMatch(item -> item.userId() == 2L
                        && item.type().equals("TEAM_RESULT")
                        && item.content().contains("closed")));
    }

    @Test
    @DisplayName("cancel rejects an unknown application")
    void cancelRejectsUnknownApplication() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));

        BizException exception = assertThrows(BizException.class,
                () -> service.cancelApplication(team.teamId(), 2L));
        assertEquals(ErrorCode.APPLICATION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("cancel rejects an approved application")
    void cancelRejectsApprovedApplication() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "hello"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        BizException exception = assertThrows(BizException.class,
                () -> service.cancelApplication(team.teamId(), 2L));
        assertEquals(ErrorCode.APPLICATION_NOT_PENDING.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("applications are only visible to the captain")
    void applicationsRejectNonCaptain() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));

        BizException exception = assertThrows(BizException.class,
                () -> service.applications(team.teamId(), 2L));
        assertEquals(ErrorCode.NOT_TEAM_CAPTAIN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("expire is a no-op before the start time")
    void expireIsNoOpBeforeStartTime() {
        TeamDetail team = service.create(command(100L, 3, "Weekend team"));

        service.expire(team.teamId());

        assertEquals(TeamStatus.RECRUITING, service.get(team.teamId(), 1L).status());
    }

    private CreateTeamCommand command(long gameId, int capacity, String title) {
        return command(gameId, capacity, title, NOW.plusHours(2));
    }

    private CreateTeamCommand command(long gameId, int capacity, String title, LocalDateTime startTime) {
        return new CreateTeamCommand(
                1L, gameId, title, "Gold", "Platinum",
                startTime, 120, capacity, "REQUIRED", "PC/Telecom-1", "ROOM-888");
    }

    private TeamApplication seedApplication(long teamId, long userId, ApplicationStatus status) {
        TeamApplication application = new TeamApplication(
                repository.nextApplicationId(), teamId, userId, "seed", NOW);
        if (status == ApplicationStatus.APPROVED) {
            application.approve();
        } else if (status == ApplicationStatus.REJECTED) {
            application.reject();
        } else if (status == ApplicationStatus.CANCELLED) {
            application.cancel();
        }
        repository.saveApplication(application);
        return application;
    }
}
