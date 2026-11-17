package com.coplay.governance;

import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import com.coplay.governance.dto.SubmitReportCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain aggregate and DTO contracts: state machine, immutability and defensive copies.
 */
class DomainModelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 0, 0);

    @Test
    @DisplayName("a new report starts pending without handler data and copies its evidence")
    void newReportStartsPending() {
        List<String> source = new ArrayList<>(List.of("a.png"));

        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                null, source, NOW);

        assertEquals(ReportStatus.PENDING, report.getStatus());
        assertTrue(report.isPending());
        assertNull(report.getHandlerId());
        assertEquals("", report.getHandleNote());
        assertNull(report.getHandledAt());
        assertEquals("", report.getDescription());
        assertEquals(NOW, report.getCreatedAt());

        source.add("b.png");
        assertEquals(List.of("a.png"), report.getEvidence());
        assertThrows(UnsupportedOperationException.class, () -> report.getEvidence().add("c.png"));
    }

    @Test
    @DisplayName("a report rejects a null target type, reason or creation time")
    void newReportRejectsNullCoreFields() {
        assertThrows(NullPointerException.class, () -> new Report(
                1L, 2L, null, 5001L, "Harassment", "", List.of(), NOW));
        assertThrows(NullPointerException.class, () -> new Report(
                1L, 2L, ReportTargetType.POST, 5001L, null, "", List.of(), NOW));
        assertThrows(NullPointerException.class, () -> new Report(
                1L, 2L, ReportTargetType.POST, 5001L, "Harassment", "", List.of(), null));
    }

    @Test
    @DisplayName("a report with null evidence and description normalizes both to empty")
    void newReportNormalizesNullCollections() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                null, null, NOW);

        assertEquals("", report.getDescription());
        assertEquals(List.of(), report.getEvidence());
    }

    @Test
    @DisplayName("resolve stores the handler, note and status and cannot run twice")
    void resolveStoresHandlerAndRejectsSecondCall() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                "", List.of(), NOW);

        report.resolve(9L, "checked");
        report.markHandledAt(NOW);

        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        assertFalse(report.isPending());
        assertEquals(9L, report.getHandlerId());
        assertEquals("checked", report.getHandleNote());
        assertEquals(NOW, report.getHandledAt());
        assertThrows(IllegalStateException.class, () -> report.resolve(9L, "again"));
    }

    @Test
    @DisplayName("reject stores the rejected status and defaults a null note")
    void rejectStoresRejectedStatus() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                "", List.of(), NOW);

        report.reject(9L, null);
        report.markHandledAt(NOW);

        assertEquals(ReportStatus.REJECTED, report.getStatus());
        assertEquals("", report.getHandleNote());
        assertEquals(NOW, report.getHandledAt());
        assertThrows(IllegalStateException.class, () -> report.reject(9L, "again"));
    }

    @Test
    @DisplayName("a pending report cannot be stamped as handled")
    void markHandledAtRejectsPendingReport() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                "", List.of(), NOW);

        assertThrows(IllegalStateException.class, () -> report.markHandledAt(NOW));
    }

    @Test
    @DisplayName("a handled report cannot receive a null handling time")
    void markHandledAtRejectsNull() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                "", List.of(), NOW);
        report.resolve(9L, "checked");

        assertThrows(NullPointerException.class, () -> report.markHandledAt(null));
    }

    @Test
    @DisplayName("appendEvidence extends the evidence list and rejects null")
    void appendEvidenceExtendsEvidence() {
        Report report = new Report(1L, 2L, ReportTargetType.POST, 5001L, "Harassment",
                "", List.of(), NOW);

        report.appendEvidence(List.of("a.png"));
        report.appendEvidence(List.of("b.png"));

        assertEquals(List.of("a.png", "b.png"), report.getEvidence());
        assertThrows(UnsupportedOperationException.class, () -> report.getEvidence().add("c.png"));
        assertThrows(NullPointerException.class, () -> report.appendEvidence(null));
    }

    @Test
    @DisplayName("admin log trims action and result and rejects null fields")
    void adminLogValidatesAndTrimsFields() {
        AdminLog log = new AdminLog(1L, 9L, " HANDLE_REPORT ", ReportTargetType.POST,
                5001L, " RESOLVED ", NOW);

        assertEquals(1L, log.getLogId());
        assertEquals(9L, log.getAdminId());
        assertEquals("HANDLE_REPORT", log.getAction());
        assertEquals(ReportTargetType.POST, log.getTargetType());
        assertEquals(5001L, log.getTargetId());
        assertEquals("RESOLVED", log.getResult());
        assertEquals(NOW, log.getCreatedAt());

        assertThrows(NullPointerException.class, () -> new AdminLog(
                1L, 9L, null, ReportTargetType.POST, 5001L, "RESOLVED", NOW));
        assertThrows(NullPointerException.class, () -> new AdminLog(
                1L, 9L, "HANDLE_REPORT", null, 5001L, "RESOLVED", NOW));
        assertThrows(NullPointerException.class, () -> new AdminLog(
                1L, 9L, "HANDLE_REPORT", ReportTargetType.POST, 5001L, null, NOW));
    }

    @Test
    @DisplayName("submit command defensively copies evidence including a null list")
    void submitCommandCopiesEvidence() {
        List<String> source = new ArrayList<>(List.of("a.png"));

        SubmitReportCommand command = new SubmitReportCommand(
                2L, ReportTargetType.POST, 5001L, "Harassment", "", source);
        source.add("b.png");

        assertEquals(List.of("a.png"), command.evidence());
        assertEquals(List.of(), new SubmitReportCommand(
                2L, ReportTargetType.POST, 5001L, "Harassment", "", null).evidence());
    }

    @Test
    @DisplayName("report detail defensively copies evidence including a null list")
    void reportDetailCopiesEvidence() {
        List<String> source = new ArrayList<>(List.of("a.png"));

        ReportDetail detail = new ReportDetail(1L, 2L, ReportTargetType.POST, 5001L,
                "Harassment", "", source, ReportStatus.PENDING, null, "", NOW, null);
        source.add("b.png");

        assertEquals(List.of("a.png"), detail.evidence());
        assertEquals(List.of(), new ReportDetail(1L, 2L, ReportTargetType.POST, 5001L,
                "Harassment", "", null, ReportStatus.PENDING, null, "", NOW, null).evidence());
    }

    @Test
    @DisplayName("stats overview defensively copies the by-type map including a null map")
    void statsOverviewCopiesByTypeMap() {
        Map<ReportTargetType, Long> source = new EnumMap<>(ReportTargetType.class);
        source.put(ReportTargetType.POST, 1L);

        StatsOverview stats = new StatsOverview(1L, 1L, 0L, 0L, source);
        source.put(ReportTargetType.COMMENT, 1L);

        assertEquals(Map.of(ReportTargetType.POST, 1L), stats.byType());
        assertEquals(Map.of(), new StatsOverview(0L, 0L, 0L, 0L, null).byType());
        assertThrows(UnsupportedOperationException.class,
                () -> stats.byType().put(ReportTargetType.USER, 1L));
    }
}
