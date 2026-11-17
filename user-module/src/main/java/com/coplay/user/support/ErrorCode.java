package com.coplay.user.support;

/**
 * Business error codes used by the user module.
 * The HTTP layer can map these stable codes to 4xx responses.
 */
public enum ErrorCode {
    USER_NOT_FOUND("USER-404", "User not found"),
    DUPLICATE_STUDENT_NO("USER-409-1", "Student number is already registered"),
    DUPLICATE_EMAIL("USER-409-2", "Email is already registered"),
    INVALID_STUDENT_NO("USER-422-4", "Student number is required"),
    INVALID_EMAIL("USER-422-1", "Email must contain @"),
    WEAK_PASSWORD("USER-422-2", "Password must be at least 8 characters"),
    INVALID_CODE("USER-422-3", "Verification code is invalid"),
    CODE_EXPIRED("USER-410", "Verification code has expired"),
    TOO_MANY_ATTEMPTS("USER-429", "Too many verification attempts"),
    USER_NOT_ACTIVE("USER-403", "User is not active"),
    INVALID_CREDENTIALS("USER-401", "Email or password is incorrect"),
    NOT_ADMIN("USER-403-1", "Only an administrator can perform this action"),
    INVALID_TOKEN("USER-401-1", "Token is invalid");

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
