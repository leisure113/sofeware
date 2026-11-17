package com.coplay.content.support;

/**
 * Business error codes used by the content module.
 * The HTTP layer can map these to 4xx responses.
 */
public enum ErrorCode {
    POST_NOT_FOUND("CONTENT-404", "Post not found"),
    COMMENT_NOT_FOUND("CONTENT-404-1", "Comment not found"),
    BOARD_NOT_FOUND("CONTENT-404-2", "Board not found"),
    BOARD_DISABLED("CONTENT-410", "Board is disabled"),
    GAME_DISABLED("CONTENT-410-1", "Game is disabled"),
    USER_NOT_ACTIVE("USER-403", "User is not active"),
    USER_MUTED("USER-403-1", "User is muted"),
    INVALID_TITLE("CONTENT-422", "Title length must be between 3 and 100"),
    INVALID_CONTENT("CONTENT-422-1", "Content length is out of range"),
    SENSITIVE_CONTENT("CONTENT-422-2", "Text contains sensitive words"),
    NOT_POST_AUTHOR("CONTENT-403", "Only the post author can edit this post");

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
