package com.coplay.team;

import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.TeamStatus;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.CreateTeamCommand;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamServiceTest {

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
    @DisplayName("create team returns recruiting team and leader can see room code")
    void createTeamSuccess() {
        TeamDetail team = service.create(createCommand(2));

        assertEquals(TeamStatus.RECRUITING, team.status());
        assertEquals(0, team.approvedCount());
        assertEquals("ROOM-888", team.roomCode());
    }

    @Test
    @DisplayName("create team rejects a start time in the past")
    void createRejectsPastStartTime() {
        CreateTeamCommand command = new CreateTeamCommand(
                1L, 100L, "Weekend team", "Gold", "Platinum",
                NOW.minusMinutes(1), 120, 2, "REQUIRED", "PC", "ROOM-888");

        BizException exception = assertThrows(BizException.class, () -> service.create(command));
        assertEquals(ErrorCode.INVALID_START_TIME.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create team rejects capacity outside 2..10")
    void createRejectsInvalidCapacity() {
        BizException exception = assertThrows(BizException.class, () -> service.create(createCommand(1)));
        assertEquals(ErrorCode.INVALID_CAPACITY.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("apply creates a pending application and notifies the captain")
    void applyNotifiesCaptain() {
        TeamDetail team = service.create(createCommand(2));

        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));

        assertEquals(ApplicationStatus.PENDING, application.status());
        assertEquals(1, notifications.countByType("TEAM_APPLY"));
        assertEquals(1L, notifications.notifications().get(0).userId());
    }

    @Test
    @DisplayName("duplicate active application is rejected")
    void applyRejectsDuplicate() {
        TeamDetail team = service.create(createCommand(3));
        service.apply(new ApplyTeamCommand(team.teamId(), 2L, "First"));

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Second")));
        assertEquals(ErrorCode.DUPLICATE_APPLICATION.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("captain cannot apply to own team")
    void captainCannotApplyToOwnTeam() {
        TeamDetail team = service.create(createCommand(3));

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 1L, "Self")));
        assertEquals(ErrorCode.CANNOT_APPLY_OWN_TEAM.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("approval fills the last slot and sets team to FULL")
    void approvalFillsTeam() {
        TeamDetail team = service.create(createCommand(2));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));

        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        TeamDetail detail = service.get(team.teamId(), 1L);
        assertEquals(TeamStatus.FULL, detail.status());
        assertEquals(1, detail.approvedCount());
        assertEquals(1, notifications.countByType("TEAM_RESULT"));
    }

    @Test
    @DisplayName("non-captain cannot approve")
    void nonCaptainCannotApprove() {
        TeamDetail team = service.create(createCommand(3));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 2L, true)));
        assertEquals(ErrorCode.NOT_TEAM_CAPTAIN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("a full team rejects new applications")
    void fullTeamRejectsApplication() {
        TeamDetail team = service.create(createCommand(2));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 3L, "One more")));
        assertEquals(ErrorCode.TEAM_FULL.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("only the captain can close the team")
    void closeTeamByCaptain() {
        TeamDetail team = service.create(createCommand(3));
        service.close(team.teamId(), 1L);

        assertEquals(TeamStatus.CLOSED, service.get(team.teamId(), 1L).status());

        BizException exception = assertThrows(BizException.class,
                () -> service.close(team.teamId(), 2L));
        assertEquals(ErrorCode.NOT_TEAM_CAPTAIN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("team expires after start time")
    void teamExpiresAfterStartTime() {
        TeamDetail team = service.create(createCommand(3, NOW.plusHours(1)));
        TeamServiceImpl laterService = new TeamServiceImpl(
                repository, users, games, notifications,
                new InMemoryTeamLockRegistry(), Clock.offset(CLOCK, Duration.ofHours(2)));

        laterService.expire(team.teamId());

        assertEquals(TeamStatus.EXPIRED, laterService.get(team.teamId(), 1L).status());
    }

    @Test
    @DisplayName("muted user cannot apply")
    void mutedUserCannotApply() {
        TeamDetail team = service.create(createCommand(3));
        users.activate(4L).mute(4L);

        BizException exception = assertThrows(BizException.class,
                () -> service.apply(new ApplyTeamCommand(team.teamId(), 4L, "Muted")));
        assertEquals(ErrorCode.USER_MUTED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("applicant can cancel a pending application")
    void applicantCanCancelApplication() {
        TeamDetail team = service.create(createCommand(3));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));

        service.cancelApplication(team.teamId(), 2L);

        var applications = service.applications(team.teamId(), 1L);
        assertTrue(applications.stream()
                .anyMatch(item -> item.applicationId() == application.applicationId()
                        && item.status() == ApplicationStatus.CANCELLED));
    }

    @Test
    @DisplayName("captain can still reject a pending application after team is full")
    void captainCanRejectPendingApplicationAfterFull() {
        TeamDetail team = service.create(createCommand(2));
        var approved = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "First"));
        var pending = service.apply(new ApplyTeamCommand(team.teamId(), 3L, "Second"));
        service.approve(new ApproveCommand(team.teamId(), approved.applicationId(), 1L, true));

        service.approve(new ApproveCommand(team.teamId(), pending.applicationId(), 1L, false));

        var applications = service.applications(team.teamId(), 1L);
        assertTrue(applications.stream()
                .anyMatch(item -> item.applicationId() == pending.applicationId()
                        && item.status() == ApplicationStatus.REJECTED));
    }

    @Test
    @DisplayName("room code is hidden from non-members")
    void roomCodeHiddenFromNonMember() {
        TeamDetail team = service.create(createCommand(3));
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        assertEquals("ROOM-888", service.get(team.teamId(), 2L).roomCode());
        assertEquals(null, service.get(team.teamId(), 3L).roomCode());
    }

    private CreateTeamCommand createCommand(int capacity) {
        return createCommand(capacity, NOW.plusHours(2));
    }

    private CreateTeamCommand createCommand(int capacity, LocalDateTime startTime) {
        return new CreateTeamCommand(
                1L, 100L, "Weekend ranked team", "Gold", "Platinum",
                startTime, 120, capacity, "REQUIRED", "PC/Telecom-1", "ROOM-888");
    }
}
