package com.coplay.governance.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Audit record written whenever an administrator handles a report.
 * The action is HANDLE_REPORT and the result is the resulting report status.
 */
public final class AdminLog {

    private final long logId;
    private final long adminId;
    private final String action;
    private final ReportTargetType targetType;
    private final long targetId;
    private final String result;
    private final LocalDateTime createdAt;

    public AdminLog(long logId,
                    long adminId,
                    String action,
                    ReportTargetType targetType,
                    long targetId,
                    String result,
                    LocalDateTime createdAt) {
        this.logId = logId;
        this.adminId = adminId;
        this.action = Objects.requireNonNull(action, "action").trim();
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = targetId;
        this.result = Objects.requireNonNull(result, "result").trim();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public long getLogId() {
        return logId;
    }

    public long getAdminId() {
        return adminId;
    }

    public String getAction() {
        return action;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public String getResult() {
        return result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
