package com.coplay.integration;

import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.integration.support.IntegrationFixture;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.TeamDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationIntegrationTest {

    private IntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntegrationFixture();
    }

    @Test
    @DisplayName("IT-09 content, team and governance actions emit the expected notifications")
    void notificationEventsAreEmittedAcrossModules() {
        long author = fixture.registerActive("41");
        long commenter = fixture.registerActive("42");
        long reporter = fixture.registerActive("43");
        long postId = fixture.createPost(author);
        fixture.contentService.addComment(
                new CreateCommentCommand(postId, commenter, "nice post"));

        assertTrue(fixture.notifications.all().stream().anyMatch(item ->
                item.type().equals("POST_COMMENT")
                        && item.userId() == author
                        && item.content().contains(String.valueOf(postId))));

        TeamDetail team = fixture.createTeam(author);
        fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), commenter, "join"));

        assertTrue(fixture.notifications.all().stream().anyMatch(item ->
                item.type().equals("TEAM_APPLY") && item.userId() == author));

        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.POST, postId, "spam", "", List.of()));
        fixture.governanceService.handle(new HandleReportCommand(
                report.reportId(), fixture.adminId, false, "not enough evidence"));

        assertTrue(fixture.notifications.all().stream().anyMatch(item ->
                item.type().equals("REPORT_RESULT") && item.userId() == reporter));
    }
}
