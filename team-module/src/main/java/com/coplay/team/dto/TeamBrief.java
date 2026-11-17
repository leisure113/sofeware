package com.coplay.team.dto;

import com.coplay.team.domain.TeamStatus;

/**
 * Lightweight team view for list pages.
 */
public record TeamBrief(
        long teamId,
        long gameId,
        String title,
        long leaderId,
        int capacity,
        int approvedCount,
        TeamStatus status) {
}
