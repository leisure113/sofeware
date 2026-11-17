package com.coplay.governance.domain;

/**
 * Lifecycle of a report: it starts PENDING and ends RESOLVED or REJECTED.
 */
public enum ReportStatus {
    PENDING,
    RESOLVED,
    REJECTED
}
