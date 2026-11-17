package com.coplay.team.dto;

import java.time.LocalDateTime;

/**
 * Command for creating a team. capacity includes the captain.
 */
public record CreateTeamCommand(
        long leaderId,
        long gameId,
        String title,
        String rankMin,
        String rankMax,
        LocalDateTime startTime,
        int durationMinutes,
        int capacity,
        String voice,
        String platform,
        String roomCode) {
}
