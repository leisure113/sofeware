package com.coplay.governance.repository;

import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for reports.
 * The production implementation would be a MyBatis repository.
 */
public interface ReportRepository {

    long nextId();

    Report save(Report report);

    Optional<Report> findById(long reportId);

    /**
     * Pending reports ordered newest first (createdAt desc, then id desc).
     */
    List<Report> findPending();

    List<Report> findAll();

    long countByStatus(ReportStatus status);
}
