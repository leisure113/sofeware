package com.coplay.integration;

import com.coplay.content.dto.CreatePostCommand;
import com.coplay.integration.support.IntegrationFixture;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.TeamDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTeamIntegrationTest {

    private IntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntegrationFixture();
    }

    @Test
    @DisplayName("IT-01 active user can create a team and another active user can join")
    void activeUserCanCreateAndJoinTeam() {
        long captain = fixture.registerActive("01");
        long member = fixture.registerActive("02");
        TeamDetail team = fixture.createTeam(captain);

        var application = fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), member, "join"));
        fixture.teamService.approve(new ApproveCommand(
                team.teamId(), application.applicationId(), captain, true));

        TeamDetail updated = fixture.teamService.get(team.teamId(), captain);
        assertEquals(1, updated.approvedCount());
        assertEquals("ROOM-INTEGRATION", fixture.teamService.get(team.teamId(), member).roomCode());
    }

    @Test
    @DisplayName("IT-02 muted user is rejected with USER_MUTED by both content and team modules")
    void mutedUserIsRejectedWithMutedCodeAcrossModules() {
        long captain = fixture.registerActive("03");
        long muted = fixture.registerActive("04");
        fixture.mute(muted);
        TeamDetail team = fixture.createTeam(captain);

        var contentError = assertThrows(com.coplay.content.support.BizException.class,
                () -> fixture.contentService.createPost(
                        new CreatePostCommand(1L, muted, "Muted post", "content")));
        assertEquals("USER-403-1", contentError.getCode());

        var teamError = assertThrows(com.coplay.team.support.BizException.class,
                () -> fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), muted, "join")));
        assertEquals("USER-403-1", teamError.getCode());
    }

    @Test
    @DisplayName("IT-10 unverified user cannot publish or apply")
    void unverifiedUserIsRejected() {
        long captain = fixture.registerActive("05");
        long pending = fixture.registerPending("06");
        TeamDetail team = fixture.createTeam(captain);

        var contentError = assertThrows(com.coplay.content.support.BizException.class,
                () -> fixture.contentService.createPost(
                        new CreatePostCommand(1L, pending, "Pending post", "content")));
        assertEquals("USER-403", contentError.getCode());

        var teamError = assertThrows(com.coplay.team.support.BizException.class,
                () -> fixture.teamService.apply(new ApplyTeamCommand(team.teamId(), pending, "join")));
        assertEquals("USER-403", teamError.getCode());
    }
}
