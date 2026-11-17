package com.coplay.governance.dto;

import com.coplay.governance.domain.ReportTargetType;

import java.util.List;

/**
 * Command for submitting a report. The evidence list is copied defensively.
 */
public record SubmitReportCommand(
        long reporterId,
        ReportTargetType targetType,
        long targetId,
        String reason,
        String description,
        List<String> evidence) {

    public SubmitReportCommand {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
