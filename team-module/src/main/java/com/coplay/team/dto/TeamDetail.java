package com.coplay.team.dto;

import com.coplay.team.domain.TeamStatus;

import java.time.LocalDateTime;

/**
 * Team detail. roomCode is null unless the viewer is the captain or an approved member.
 */
public record TeamDetail(
        long teamId,
        long gameId,
        String title,
        long leaderId,
        String rankMin,
        String rankMax,
        LocalDateTime startTime,
        int durationMinutes,
        int capacity,
        int approvedCount,
        String voice,
        String platform,
        String roomCode,
        TeamStatus status) {
}
