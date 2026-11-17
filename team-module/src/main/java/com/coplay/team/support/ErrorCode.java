package com.coplay.team.support;

/**
 * Business error codes used by the team module.
 * The HTTP layer can map these to 4xx responses.
 */
public enum ErrorCode {
    TEAM_NOT_FOUND("TEAM-404", "Team not found"),
    GAME_NOT_FOUND("GAME-404", "Game not found or disabled"),
    USER_NOT_ACTIVE("USER-403", "User is not active"),
    USER_MUTED("USER-403-1", "User is muted"),
    TEAM_CLOSED("TEAM-410", "Team is closed or expired"),
    TEAM_FULL("TEAM-409", "Team is full"),
    DUPLICATE_APPLICATION("TEAM-409-1", "An active application already exists"),
    CANNOT_APPLY_OWN_TEAM("TEAM-422", "Captain cannot apply to own team"),
    NOT_TEAM_CAPTAIN("TEAM-403-2", "Only the captain can perform this action"),
    APPLICATION_NOT_FOUND("TEAM-404-2", "Application not found"),
    APPLICATION_NOT_PENDING("TEAM-409-2", "Application is not pending"),
    INVALID_CAPACITY("TEAM-422-1", "Capacity must be between 2 and 10"),
    INVALID_START_TIME("TEAM-422-2", "Start time must be in the future"),
    INVALID_TITLE("TEAM-422-3", "Title length must be between 5 and 50"),
    INVALID_DURATION("TEAM-422-4", "Duration must be between 30 and 480 minutes"),
    INVALID_VOICE("TEAM-422-5", "Voice requirement must be REQUIRED, OPTIONAL or NONE");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
