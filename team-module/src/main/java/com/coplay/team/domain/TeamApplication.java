package com.coplay.team.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Application for a team. APPROVED applications are team members.
 */
public final class TeamApplication {

    private final long applicationId;
    private final long teamId;
    private final long userId;
    private final String message;
    private final LocalDateTime createdAt;
    private ApplicationStatus status;

    public TeamApplication(long applicationId,
                           long teamId,
                           long userId,
                           String message,
                           LocalDateTime createdAt) {
        this.applicationId = applicationId;
        this.teamId = teamId;
        this.userId = userId;
        this.message = Objects.requireNonNullElse(message, "");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = ApplicationStatus.PENDING;
    }

    public long getApplicationId() {
        return applicationId;
    }

    public long getTeamId() {
        return teamId;
    }

    public long getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public boolean isPending() {
        return status == ApplicationStatus.PENDING;
    }

    public boolean isActive() {
        return status == ApplicationStatus.PENDING || status == ApplicationStatus.APPROVED;
    }

    public void approve() {
        requirePending("approve");
        status = ApplicationStatus.APPROVED;
    }

    public void reject() {
        requirePending("reject");
        status = ApplicationStatus.REJECTED;
    }

    public void cancel() {
        requirePending("cancel");
        status = ApplicationStatus.CANCELLED;
    }

    public void leave() {
        if (status != ApplicationStatus.APPROVED) {
            throw new IllegalStateException("Only an approved member can leave");
        }
        status = ApplicationStatus.LEFT;
    }

    private void requirePending(String action) {
        if (!isPending()) {
            throw new IllegalStateException("Cannot " + action + " an application in status " + status);
        }
    }
}
