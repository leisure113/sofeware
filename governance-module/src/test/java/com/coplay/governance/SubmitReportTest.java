package com.coplay.governance;

import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.governance.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * submit(): normal, boundary and exceptional paths.
 */
class SubmitReportTest extends GovernanceTestSupport {

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    @DisplayName("submit accepts every target type and stores a PENDING report")
    void submitAcceptsEveryTargetType(ReportTargetType targetType) {
        ReportDetail report = submit(REPORTER_ID, targetType, 5001L + targetType.ordinal());

        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals(targetType, report.targetType());
        assertEquals(5001L + targetType.ordinal(), report.targetId());
        assertEquals(REPORTER_ID, report.reporterId());
        assertEquals(NOW, report.createdAt());
        assertTrue(reports.findById(report.reportId()).orElseThrow().isPending());
    }

    @Test
    @DisplayName("submit trims reason and description but keeps evidence order")
    void submitTrimsTextFieldsAndKeepsEvidenceOrder() {
        ReportDetail report = service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "  Harassment  ", "  details  ",
                List.of("shot-1.png", "shot-2.png")));

        assertEquals("Harassment", report.reason());
        assertEquals("details", report.description());
        assertEquals(List.of("shot-1.png", "shot-2.png"), report.evidence());
    }

    @Test
    @DisplayName("submit accepts a reason of exactly two characters")
    void submitAcceptsMinimumReasonLength() {
        ReportDetail report = service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "ab", "", List.of()));

        assertEquals("ab", report.reason());
        assertEquals(1, reports.findAll().size());
    }

    @Test
    @DisplayName("submit accepts a reason of exactly 100 characters")
    void submitAcceptsMaximumReasonLength() {
        String reason = "a".repeat(100);

        ReportDetail report = service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, reason, "", List.of()));

        assertEquals(100, report.reason().length());
        assertEquals(reason, report.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "x", " x "})
    @DisplayName("submit rejects reasons shorter than two characters after trimming")
    void submitRejectsShortReasons(String reason) {
        assertCode(ErrorCode.INVALID_REASON, () -> service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, reason, "", List.of())));

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects a null reason and a reason of 101 characters")
    void submitRejectsNullAndOverlongReason() {
        assertCode(ErrorCode.INVALID_REASON, () -> service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, null, "", List.of())));
        assertCode(ErrorCode.INVALID_REASON, () -> service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "a".repeat(101), "", List.of())));

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit accepts a description of exactly 500 characters")
    void submitAcceptsMaximumDescriptionLength() {
        String description = "a".repeat(500);

        ReportDetail report = service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "Harassment", description, List.of()));

        assertEquals(500, report.description().length());
    }

    @Test
    @DisplayName("submit rejects a description of 501 characters")
    void submitRejectsOverlongDescription() {
        assertCode(ErrorCode.INVALID_REASON, () -> service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "Harassment", "a".repeat(501), List.of())));

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit treats null description and null evidence as empty values")
    void submitAcceptsNullDescriptionAndEvidence() {
        ReportDetail report = service.submit(new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "Harassment", null, null));

        assertEquals("", report.description());
        assertEquals(List.of(), report.evidence());
    }

    @Test
    @DisplayName("submit accepts exactly three evidence items")
    void submitAcceptsThreeEvidenceItems() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L,
                "a.png", "b.png", "c.png");

        assertEquals(3, report.evidence().size());
    }

    @Test
    @DisplayName("submit rejects four evidence items")
    void submitRejectsFourEvidenceItems() {
        assertCode(ErrorCode.TOO_MANY_EVIDENCE, () -> submit(REPORTER_ID, ReportTargetType.POST,
                5001L, "a.png", "b.png", "c.png", "d.png"));

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects an inactive reporter without creating a report")
    void submitRejectsInactiveReporter() {
        users.deactivate(REPORTER_ID);

        assertCode(ErrorCode.INVALID_OPERATOR, () -> service.submit(postReport()));

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects an unknown reporter id")
    void submitRejectsUnknownReporter() {
        assertCode(ErrorCode.INVALID_OPERATOR, () -> submit(99L, ReportTargetType.POST, 5001L));
        assertTrue(reports.findAll().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("submit rejects a non-positive reporter id")
    void submitRejectsNonPositiveReporter(long reporterId) {
        assertCode(ErrorCode.INVALID_OPERATOR,
                () -> submit(reporterId, ReportTargetType.POST, 5001L));
        assertTrue(reports.findAll().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("submit rejects a zero or negative target id")
    void submitRejectsInvalidTargetId(long targetId) {
        assertCode(ErrorCode.INVALID_TARGET,
                () -> submit(REPORTER_ID, ReportTargetType.POST, targetId));
        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects a null target type")
    void submitRejectsNullTargetType() {
        assertCode(ErrorCode.INVALID_TARGET, () -> service.submit(new SubmitReportCommand(
                REPORTER_ID, null, 5001L, "Harassment", "", List.of())));
        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects a second PENDING report for the same reporter and target")
    void submitRejectsDuplicatePendingReport() {
        ReportDetail first = submitPostReport();

        assertCode(ErrorCode.DUPLICATE_REPORT, () -> submitPostReport());

        assertEquals(1, reports.findAll().size());
        assertEquals(ReportStatus.PENDING, reports.findById(first.reportId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("submit allows the same target again after the previous report was resolved")
    void submitAllowsDuplicateAfterResolvedReport() {
        ReportDetail first = submitPostReport();
        service.handle(handle(first.reportId(), true));

        ReportDetail second = submitPostReport();

        assertNotEquals(first.reportId(), second.reportId());
        assertEquals(2, reports.findAll().size());
        assertEquals(ReportStatus.PENDING, second.status());
    }

    @Test
    @DisplayName("submit allows the same target again after the previous report was rejected")
    void submitAllowsDuplicateAfterRejectedReport() {
        ReportDetail first = submitPostReport();
        service.handle(handle(first.reportId(), false));

        ReportDetail second = submitPostReport();

        assertNotEquals(first.reportId(), second.reportId());
        assertEquals(2, reports.findAll().size());
    }

    @Test
    @DisplayName("duplicate detection only considers PENDING reports of the same reporter")
    void submitDuplicateCheckOnlyTracksPendingReports() {
        ReportDetail first = submitPostReport();
        service.handle(handle(first.reportId(), true));
        ReportDetail second = submitPostReport();

        assertCode(ErrorCode.DUPLICATE_REPORT, () -> submitPostReport());

        assertEquals(2, reports.findAll().size());
        assertEquals(second.reportId(), reports.findPending().get(0).getId());
    }

    @Test
    @DisplayName("submit allows the same target from a different reporter")
    void submitAllowsSameTargetFromAnotherReporter() {
        submitPostReport();
        ReportDetail other = submit(OTHER_REPORTER_ID, ReportTargetType.POST, 5001L);

        assertEquals(OTHER_REPORTER_ID, other.reporterId());
        assertEquals(2, reports.findAll().size());
    }

    @Test
    @DisplayName("submit allows the same reporter on a different target id or type")
    void submitAllowsSameReporterOnDifferentTargets() {
        submit(REPORTER_ID, ReportTargetType.POST, 5001L);
        submit(REPORTER_ID, ReportTargetType.POST, 5002L);
        submit(REPORTER_ID, ReportTargetType.COMMENT, 5001L);
        submit(REPORTER_ID, ReportTargetType.USER, 5001L);
        submit(REPORTER_ID, ReportTargetType.TEAM, 5001L);

        assertEquals(5, reports.findAll().size());
    }
}
