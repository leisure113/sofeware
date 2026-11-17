package com.coplay.governance.dto;

import com.coplay.governance.domain.ReportTargetType;

import java.util.Map;

/**
 * Aggregate report statistics. byType always contains every target type,
 * including types with a zero count.
 */
public record StatsOverview(
        long total,
        long pending,
        long resolved,
        long rejected,
        Map<ReportTargetType, Long> byType) {

    public StatsOverview {
        byType = byType == null ? Map.of() : Map.copyOf(byType);
    }
}
