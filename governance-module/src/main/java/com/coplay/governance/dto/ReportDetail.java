package com.coplay.governance.dto;

import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full report view. handlerId, handleNote and handledAt are empty/null while pending.
 */
public record ReportDetail(
        long reportId,
        long reporterId,
        ReportTargetType targetType,
        long targetId,
        String reason,
        String description,
        List<String> evidence,
        ReportStatus status,
        Long handlerId,
        String handleNote,
        LocalDateTime createdAt,
        LocalDateTime handledAt) {

    public ReportDetail {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
