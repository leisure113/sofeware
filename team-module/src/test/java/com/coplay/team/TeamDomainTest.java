package com.coplay.team;

import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.Team;
import com.coplay.team.domain.TeamApplication;
import com.coplay.team.domain.TeamStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamDomainTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 20, 0);

    @Test
    @DisplayName("markFull is idempotent")
    void markFullIsIdempotent() {
        Team team = team();

        team.markFull();
        team.markFull();

        assertEquals(TeamStatus.FULL, team.getStatus());
    }

    @Test
    @DisplayName("full team can be closed")
    void fullTeamCanBeClosed() {
        Team team = team();
        team.markFull();

        team.close();

        assertEquals(TeamStatus.CLOSED, team.getStatus());
    }

    @Test
    @DisplayName("closed team cannot be closed again")
    void closedTeamCannotBeClosedAgain() {
        Team team = team();
        team.close();

        assertThrows(IllegalStateException.class, team::close);
    }

    @Test
    @DisplayName("expired team cannot expire again")
    void expiredTeamCannotExpireAgain() {
        Team team = team();
        team.expire();

        assertThrows(IllegalStateException.class, team::expire);
    }

    @Test
    @DisplayName("team is expired at the start time, not before")
    void expirationBoundary() {
        Team team = team();

        assertFalse(team.isExpiredAt(START.minusSeconds(1)));
        assertTrue(team.isExpiredAt(START));
        assertTrue(team.isExpiredAt(START.plusMinutes(1)));
    }

    @Test
    @DisplayName("application approval moves PENDING to APPROVED")
    void approveApplication() {
        TeamApplication application = application();

        application.approve();

        assertEquals(ApplicationStatus.APPROVED, application.getStatus());
        assertTrue(application.isActive());
    }

    @Test
    @DisplayName("approved application cannot be approved again")
    void approveTwiceFails() {
        TeamApplication application = application();
        application.approve();

        assertThrows(IllegalStateException.class, application::approve);
    }

    @Test
    @DisplayName("approved application cannot be rejected")
    void rejectApprovedApplicationFails() {
        TeamApplication application = application();
        application.approve();

        assertThrows(IllegalStateException.class, application::reject);
    }

    @Test
    @DisplayName("rejected application cannot be cancelled")
    void cancelRejectedApplicationFails() {
        TeamApplication application = application();
        application.reject();

        assertThrows(IllegalStateException.class, application::cancel);
    }

    private Team team() {
        return new Team(1L, 100L, 1L, "Weekend team", "Gold", "Platinum",
                START, 120, 3, "REQUIRED", "PC", "ROOM-1");
    }

    private TeamApplication application() {
        return new TeamApplication(1L, 1L, 2L, "hello", START.minusHours(1));
    }
}
