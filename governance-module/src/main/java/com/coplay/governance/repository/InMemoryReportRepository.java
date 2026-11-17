package com.coplay.governance.repository;

import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository used by the standalone demo and tests.
 * All mutating operations are synchronized so the module is safe without a database.
 */
public final class InMemoryReportRepository implements ReportRepository {

    private final Map<Long, Report> reports = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(2000);

    @Override
    public long nextId() {
        return ids.incrementAndGet();
    }

    @Override
    public synchronized Report save(Report report) {
        reports.put(report.getId(), report);
        return report;
    }

    @Override
    public Optional<Report> findById(long reportId) {
        return Optional.ofNullable(reports.get(reportId));
    }

    @Override
    public List<Report> findPending() {
        return reports.values().stream()
                .filter(Report::isPending)
                .sorted(Comparator.comparing(Report::getCreatedAt)
                        .thenComparingLong(Report::getId)
                        .reversed())
                .toList();
    }

    @Override
    public List<Report> findAll() {
        return List.copyOf(reports.values());
    }

    @Override
    public long countByStatus(ReportStatus status) {
        return reports.values().stream()
                .filter(report -> report.getStatus() == status)
                .count();
    }
}
