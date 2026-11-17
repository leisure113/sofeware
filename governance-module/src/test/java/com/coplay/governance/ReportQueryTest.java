package com.coplay.governance;

import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.ReportBrief;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * listPending() and stats(): ordering, filtering and aggregation.
 */
class ReportQueryTest extends GovernanceTestSupport {

    @Test
    @DisplayName("listPending returns reports newest first")
    void listPendingReturnsNewestFirst() {
        ReportDetail first = submit(REPORTER_ID, ReportTargetType.POST, 5001L);
        ReportDetail second = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L);
        ReportDetail third = submit(REPORTER_ID, ReportTargetType.COMMENT, 6001L);

        List<Long> ids = service.listPending().stream().map(ReportBrief::reportId).toList();

        assertEquals(List.of(third.reportId(), second.reportId(), first.reportId()), ids);
        assertTrue(service.listPending().stream()
                .allMatch(brief -> brief.status() == ReportStatus.PENDING));
    }

    @Test
    @DisplayName("listPending no longer contains reports that were handled")
    void listPendingExcludesHandledReports() {
        ReportDetail first = submit(REPORTER_ID, ReportTargetType.POST, 5001L);
        ReportDetail second = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L);
        service.handle(handle(second.reportId(), true));

        List<Long> ids = service.listPending().stream().map(ReportBrief::reportId).toList();

        assertEquals(List.of(first.reportId()), ids);
    }

    @Test
    @DisplayName("listPending is empty when there is no report")
    void listPendingIsEmptyWithoutReports() {
        assertTrue(service.listPending().isEmpty());
    }

    @Test
    @DisplayName("listPending is empty after every report was handled")
    void listPendingIsEmptyAfterAllHandled() {
        ReportDetail first = submit(REPORTER_ID, ReportTargetType.POST, 5001L);
        ReportDetail second = submit(REPORTER_ID, ReportTargetType.USER, 3001L);
        service.handle(handle(first.reportId(), true));
        service.handle(handle(second.reportId(), false));

        assertTrue(service.listPending().isEmpty());
    }

    @Test
    @DisplayName("listPending brief exposes the report summary fields")
    void listPendingMapsBriefFields() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.COMMENT, 6001L);

        ReportBrief brief = service.listPending().get(0);

        assertEquals(report.reportId(), brief.reportId());
        assertEquals(REPORTER_ID, brief.reporterId());
        assertEquals(ReportTargetType.COMMENT, brief.targetType());
        assertEquals(6001L, brief.targetId());
        assertEquals("Harassment", brief.reason());
        assertEquals(ReportStatus.PENDING, brief.status());
        assertEquals(NOW, brief.createdAt());
    }

    @Test
    @DisplayName("stats counts reports by status and by target type")
    void statsCountsByStatusAndTargetType() {
        ReportDetail postOne = submit(REPORTER_ID, ReportTargetType.POST, 5001L);
        ReportDetail postTwo = submit(REPORTER_ID, ReportTargetType.POST, 5002L);
        ReportDetail commentReport = submit(OTHER_REPORTER_ID, ReportTargetType.COMMENT, 6001L);
        ReportDetail userReport = submit(1L, ReportTargetType.USER, 3001L);
        ReportDetail teamReport = submit(4L, ReportTargetType.TEAM, 4001L);

        service.handle(handle(postOne.reportId(), true));
        service.handle(handle(userReport.reportId(), false));

        StatsOverview stats = service.stats();

        assertEquals(5L, stats.total());
        assertEquals(3L, stats.pending());
        assertEquals(1L, stats.resolved());
        assertEquals(1L, stats.rejected());
        assertEquals(Map.of(
                        ReportTargetType.POST, 2L,
                        ReportTargetType.COMMENT, 1L,
                        ReportTargetType.USER, 1L,
                        ReportTargetType.TEAM, 1L),
                Map.copyOf(stats.byType()));
        assertEquals(Set.of(ReportTargetType.values()), stats.byType().keySet());
        assertEquals(stats.total(), stats.byType().values().stream()
                .mapToLong(Long::longValue).sum());
        assertEquals(List.of(teamReport.reportId(), commentReport.reportId(), postTwo.reportId()),
                service.listPending().stream().map(ReportBrief::reportId).toList());
    }

    @Test
    @DisplayName("stats is all zeros on an empty repository and still lists every target type")
    void statsIsZeroWithoutReports() {
        StatsOverview stats = service.stats();

        assertEquals(0L, stats.total());
        assertEquals(0L, stats.pending());
        assertEquals(0L, stats.resolved());
        assertEquals(0L, stats.rejected());
        assertEquals(4, stats.byType().size());
        assertEquals(Set.of(ReportTargetType.values()), stats.byType().keySet());
        assertTrue(stats.byType().values().stream().allMatch(count -> count == 0L));
    }
}
