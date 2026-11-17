package com.coplay.team.dto;

import com.coplay.team.domain.ApplicationStatus;

/**
 * Application view returned to the applicant or captain.
 */
public record ApplicationBrief(
        long applicationId,
        long teamId,
        long userId,
        String message,
        ApplicationStatus status) {
}
