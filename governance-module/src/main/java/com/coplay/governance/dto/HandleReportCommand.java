package com.coplay.governance.dto;

/**
 * Command for an administrator to handle a pending report.
 * valid = true resolves the report and triggers the target moderation action;
 * valid = false rejects the report.
 */
public record HandleReportCommand(
        long reportId,
        long operatorId,
        boolean valid,
        String note) {
}
