package com.coplay.team.dto;

/**
 * Command for approving or rejecting an application.
 */
public record ApproveCommand(long teamId, long applicationId, long captainId, boolean approve) {
}
