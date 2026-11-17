package com.coplay.team.dto;

/**
 * Command for applying to a team.
 */
public record ApplyTeamCommand(long teamId, long userId, String message) {
}
