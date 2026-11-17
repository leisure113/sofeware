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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD tests for the new leaveTeam use case.
 * These tests are written before the implementation and are expected to fail first.
 */
class TeamLeaveTddTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private InMemoryTeamRepository repository;
    private FakeNotificationService notifications;
    private TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTeamRepository();
        notifications = new FakeNotificationService();
        service = new TeamServiceImpl(
                repository,
                new FakeUserQueryService(),
                new FakeGameQueryService(),
                notifications,
                new InMemoryTeamLockRegistry(),
                CLOCK);
    }

    @Test
    @DisplayName("approved member leaves a full team, team reopens and slot is freed")
    void approvedMemberLeavesFullTeam() {
        TeamDetail team = createTeam(2);
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "jungle"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        service.leaveTeam(team.teamId(), 2L);

        TeamDetail detail = service.get(team.teamId(), 1L);
        assertEquals(TeamStatus.RECRUITING, detail.status());
        assertEquals(0, detail.approvedCount());
        assertEquals(ApplicationStatus.LEFT, repository.findApplications(team.teamId()).get(0).getStatus());
        assertTrue(notifications.notifications().stream()
                .anyMatch(item -> item.type().equals("TEAM_MEMBER_LEFT")));
    }

    @Test
    @DisplayName("non-member cannot leave a team")
    void nonMemberCannotLeaveTeam() {
        TeamDetail team = createTeam(3);
        service.apply(new ApplyTeamCommand(team.teamId(), 2L, "pending"));

        BizException exception = assertThrows(BizException.class,
                () -> service.leaveTeam(team.teamId(), 3L));
        assertEquals(ErrorCode.NOT_TEAM_MEMBER.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("captain cannot leave the team")
    void captainCannotLeaveTeam() {
        TeamDetail team = createTeam(3);

        BizException exception = assertThrows(BizException.class,
                () -> service.leaveTeam(team.teamId(), 1L));
        assertEquals(ErrorCode.CAPTAIN_CANNOT_LEAVE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("member leaving a recruiting team keeps it recruiting")
    void memberLeavesRecruitingTeam() {
        TeamDetail team = createTeam(3);
        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "jungle"));
        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        service.leaveTeam(team.teamId(), 2L);

        assertEquals(TeamStatus.RECRUITING, service.get(team.teamId(), 1L).status());
        assertEquals(0, service.get(team.teamId(), 1L).approvedCount());
    }

    private TeamDetail createTeam(int capacity) {
        return service.create(new CreateTeamCommand(
                1L, 100L, "Weekend ranked team", "Gold", "Platinum",
                NOW.plusHours(2), 120, capacity, "REQUIRED", "PC", "ROOM-888"));
    }
}
