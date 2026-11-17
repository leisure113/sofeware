package com.coplay.team.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Team aggregate root. Capacity includes the captain.
 */
public final class Team {

    private final long teamId;
    private final long gameId;
    private final long leaderId;
    private final String title;
    private final String rankMin;
    private final String rankMax;
    private final LocalDateTime startTime;
    private final int durationMinutes;
    private final int capacity;
    private final String voice;
    private final String platform;
    private final String roomCode;
    private TeamStatus status;

    public Team(long teamId,
                long gameId,
                long leaderId,
                String title,
                String rankMin,
                String rankMax,
                LocalDateTime startTime,
                int durationMinutes,
                int capacity,
                String voice,
                String platform,
                String roomCode) {
        this.teamId = teamId;
        this.gameId = gameId;
        this.leaderId = leaderId;
        this.title = Objects.requireNonNull(title, "title");
        this.rankMin = Objects.requireNonNull(rankMin, "rankMin");
        this.rankMax = Objects.requireNonNull(rankMax, "rankMax");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.durationMinutes = durationMinutes;
        this.capacity = capacity;
        this.voice = Objects.requireNonNull(voice, "voice");
        this.platform = platform;
        this.roomCode = roomCode;
        this.status = TeamStatus.RECRUITING;
    }

    public long getTeamId() {
        return teamId;
    }

    public long getGameId() {
        return gameId;
    }

    public long getLeaderId() {
        return leaderId;
    }

    public String getTitle() {
        return title;
    }

    public String getRankMin() {
        return rankMin;
    }

    public String getRankMax() {
        return rankMax;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getVoice() {
        return voice;
    }

    public String getPlatform() {
        return platform;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public TeamStatus getStatus() {
        return status;
    }

    public boolean isRecruiting() {
        return status == TeamStatus.RECRUITING;
    }

    public boolean isOpen() {
        return status == TeamStatus.RECRUITING || status == TeamStatus.FULL;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return isOpen() && !now.isBefore(startTime);
    }

    public void markFull() {
        if (status == TeamStatus.FULL) {
            return;
        }
        if (status != TeamStatus.RECRUITING) {
            throw new IllegalStateException("Only a recruiting team can become full");
        }
        status = TeamStatus.FULL;
    }

    public void reopen() {
        if (status == TeamStatus.RECRUITING) {
            return;
        }
        if (status != TeamStatus.FULL) {
            throw new IllegalStateException("Only a full team can reopen");
        }
        status = TeamStatus.RECRUITING;
    }

    public void close() {
        requireOpen("close");
        status = TeamStatus.CLOSED;
    }

    public void expire() {
        requireOpen("expire");
        status = TeamStatus.EXPIRED;
    }

    private void requireOpen(String action) {
        if (!isOpen()) {
            throw new IllegalStateException("Cannot " + action + " a team in status " + status);
        }
    }
}
