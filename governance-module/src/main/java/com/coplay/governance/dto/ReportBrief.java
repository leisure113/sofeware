package com.coplay.governance.dto;

import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;

import java.time.LocalDateTime;

/**
 * Compact report view used by the pending work list.
 */
public record ReportBrief(
        long reportId,
        long reporterId,
        ReportTargetType targetType,
        long targetId,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt) {
}
