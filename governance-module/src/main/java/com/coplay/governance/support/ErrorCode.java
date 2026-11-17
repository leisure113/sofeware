package com.coplay.governance.support;

/**
 * Business error codes used by the governance module.
 * The HTTP layer can map these to 4xx/5xx responses.
 */
public enum ErrorCode {
    REPORT_NOT_FOUND("GOV-404", "Report not found"),
    NOT_ADMIN("GOV-403", "Operator is not an administrator"),
    REPORT_NOT_PENDING("GOV-409", "Report is not pending"),
    DUPLICATE_REPORT("GOV-409-1", "A pending report for the same target already exists"),
    INVALID_TARGET("GOV-422", "Target type and target id must be valid"),
    INVALID_REASON("GOV-422-1", "Reason length must be between 2 and 100, description at most 500"),
    TOO_MANY_EVIDENCE("GOV-422-2", "At most 3 evidence items are allowed"),
    INVALID_OPERATOR("GOV-403-1", "User id is invalid or the user is not active"),
    TARGET_ACTION_FAILED("GOV-502", "Failed to apply the action to the reported target");

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
