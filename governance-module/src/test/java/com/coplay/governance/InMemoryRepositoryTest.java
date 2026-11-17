package com.coplay.governance;

import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.repository.InMemoryAdminLogRepository;
import com.coplay.governance.repository.InMemoryReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory repository behaviour: identifiers, ordering, filtering and counting.
 */
class InMemoryRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 0, 0);

    private InMemoryReportRepository reports;
    private InMemoryAdminLogRepository adminLogs;

    @BeforeEach
    void setUp() {
        reports = new InMemoryReportRepository();
        adminLogs = new InMemoryAdminLogRepository();
    }

    @Test
    @DisplayName("report ids increase monotonically")
    void reportIdsIncrease() {
        long first = reports.nextId();
        long second = reports.nextId();

        assertEquals(first + 1, second);
    }

    @Test
    @DisplayName("save stores a report that can be loaded by id")
    void saveAndFindById() {
        Report report = report(1L, NOW);

        assertSame(report, reports.save(report));
        assertEquals(Optional.of(report), reports.findById(1L));
        assertEquals(1, reports.findAll().size());
    }

    @Test
    @DisplayName("findById returns empty for an unknown id")
    void findByIdUnknownId() {
        assertTrue(reports.findById(9999L).isEmpty());
    }

    @Test
    @DisplayName("findPending orders reports by createdAt descending then id descending")
    void findPendingOrdersByTimeThenId() {
        reports.save(report(1L, NOW.minusHours(2)));
        reports.save(report(2L, NOW.minusHours(1)));
        reports.save(report(3L, NOW.minusHours(1)));

        List<Long> ids = reports.findPending().stream().map(Report::getId).toList();

        assertEquals(List.of(3L, 2L, 1L), ids);
    }

    @Test
    @DisplayName("findPending only contains PENDING reports")
    void findPendingSkipsHandledReports() {
        Report first = report(1L, NOW.minusHours(2));
        Report second = report(2L, NOW.minusHours(1));
        Report third = report(3L, NOW.minusHours(1));
        reports.save(first);
        reports.save(second);
        reports.save(third);
        second.resolve(9L, "handled");

        List<Long> ids = reports.findPending().stream().map(Report::getId).toList();

        assertEquals(List.of(3L, 1L), ids);
    }

    @Test
    @DisplayName("countByStatus counts each lifecycle state independently")
    void countByStatus() {
        Report pending = report(1L, NOW);
        Report resolvedOne = report(2L, NOW);
        Report resolvedTwo = report(3L, NOW);
        Report rejected = report(4L, NOW);
        resolvedOne.resolve(9L, "ok");
        resolvedTwo.resolve(9L, "ok");
        rejected.reject(9L, "no");
        reports.save(pending);
        reports.save(resolvedOne);
        reports.save(resolvedTwo);
        reports.save(rejected);

        assertEquals(1L, reports.countByStatus(ReportStatus.PENDING));
        assertEquals(2L, reports.countByStatus(ReportStatus.RESOLVED));
        assertEquals(1L, reports.countByStatus(ReportStatus.REJECTED));
    }

    @Test
    @DisplayName("findAll returns a defensive copy of every report")
    void findAllReturnsEveryReport() {
        reports.save(report(1L, NOW));
        reports.save(report(2L, NOW));

        List<Report> all = reports.findAll();

        assertEquals(2, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(report(3L, NOW)));
    }

    @Test
    @DisplayName("admin log ids increase and saved logs are returned oldest first")
    void adminLogRepositoryKeepsInsertionOrder() {
        long firstId = adminLogs.nextId();
        long secondId = adminLogs.nextId();
        AdminLog first = new AdminLog(firstId, 9L, "HANDLE_REPORT",
                ReportTargetType.POST, 5001L, "RESOLVED", NOW);
        AdminLog second = new AdminLog(secondId, 9L, "HANDLE_REPORT",
                ReportTargetType.TEAM, 4001L, "REJECTED", NOW);

        assertSame(first, adminLogs.save(first));
        adminLogs.save(second);

        assertEquals(List.of(first, second), adminLogs.findAll());
        assertTrue(adminLogs.nextId() > secondId);
    }

    private static Report report(long id, LocalDateTime createdAt) {
        return new Report(id, 2L, ReportTargetType.POST, 5001L + id, "Harassment", "",
                List.of(), createdAt);
    }
}
