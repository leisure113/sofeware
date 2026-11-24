package com.coplay.integration;

import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.integration.support.IntegrationFixture;
import com.coplay.team.domain.TeamStatus;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.TeamDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceModerationIntegrationTest {

    private IntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntegrationFixture();
    }

    @Test
    @DisplayName("IT-05 reported user is muted and can no longer apply to a team")
    void reportedUserIsMutedAndCannotApply() {
        long captain = fixture.registerActive("21");
        long target = fixture.registerActive("22");
        long reporter = fixture.registerActive("23");
        TeamDetail team = fixture.createTeam(captain);

        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.USER, target, "toxic", "abusive language", List.of()));
        fixture.governanceService.handle(new HandleReportCommand(
                report.reportId(), fixture.adminId, true, "muted for 7 days"));

        assertTrue(fixture.findUser(target).isMuted());
        var error = assertThrows(com.coplay.team.support.BizException.class,
                () -> fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), target, "join")));
        assertEquals("USER-403-1", error.getCode());
    }

    @Test
    @DisplayName("IT-06 reported team is closed and can no longer accept applications")
    void reportedTeamIsClosed() {
        long captain = fixture.registerActive("24");
        long reporter = fixture.registerActive("25");
        TeamDetail team = fixture.createTeam(captain);

        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.TEAM, team.teamId(), "cheating", "", List.of()));
        fixture.governanceService.handle(new HandleReportCommand(
                report.reportId(), fixture.adminId, true, "confirmed cheating"));

        assertEquals(TeamStatus.CLOSED, fixture.teamService.get(team.teamId(), captain).status());
        var error = assertThrows(com.coplay.team.support.BizException.class,
                () -> fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), reporter, "join")));
        assertEquals("TEAM-410", error.getCode());
    }
}
