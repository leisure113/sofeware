package com.coplay.governance.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Report aggregate root.
 *
 * <p>A report starts as PENDING. An administrator either resolves it (the target
 * moderation action is applied) or rejects it. Both transitions are terminal.</p>
 */
public final class Report {

    private final long id;
    private final long reporterId;
    private final ReportTargetType targetType;
    private final long targetId;
    private final String reason;
    private final String description;
    private final List<String> evidence;
    private final LocalDateTime createdAt;
    private ReportStatus status;
    private Long handlerId;
    private String handleNote;
    private LocalDateTime handledAt;

    public Report(long id,
                  long reporterId,
                  ReportTargetType targetType,
                  long targetId,
                  String reason,
                  String description,
                  List<String> evidence,
                  LocalDateTime createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = targetId;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.description = Objects.requireNonNullElse(description, "");
        this.evidence = List.copyOf(Objects.requireNonNullElse(evidence, List.of()));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = ReportStatus.PENDING;
        this.handleNote = "";
    }

    public long getId() {
        return id;
    }

    public long getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

    public void resolve(long handlerId, String note) {
        requirePending("resolve");
        status = ReportStatus.RESOLVED;
        applyHandler(handlerId, note);
    }

    public void reject(long handlerId, String note) {
        requirePending("reject");
        status = ReportStatus.REJECTED;
        applyHandler(handlerId, note);
    }

    /**
     * Stamps the authoritative handling time from the service clock.
     * resolve/reject fall back to the wall clock when the aggregate is used directly.
     */
    public void markHandledAt(LocalDateTime handledAt) {
        if (isPending()) {
            throw new IllegalStateException("Cannot stamp handledAt on a pending report");
        }
        this.handledAt = Objects.requireNonNull(handledAt, "handledAt");
    }

    private void applyHandler(long handlerId, String note) {
        this.handlerId = handlerId;
        this.handleNote = Objects.requireNonNullElse(note, "");
        this.handledAt = LocalDateTime.now();
    }

    private void requirePending(String action) {
        if (!isPending()) {
            throw new IllegalStateException("Cannot " + action + " a report in status " + status);
        }
    }
}
