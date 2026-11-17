package com.coplay.governance;

import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD specification for addEvidence(): append evidence to a pending report.
 *
 * <p>Written before the implementation. Only the reporter may append, the report must
 * still be PENDING, the report must exist and the combined evidence list may not exceed
 * three items.</p>
 */
class AddEvidenceTest extends GovernanceTestSupport {

    @Test
    @DisplayName("the reporter can append evidence to a pending report")
    void reporterAppendsEvidenceToPendingReport() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L, "a.png");

        ReportDetail updated = service.addEvidence(report.reportId(), REPORTER_ID,
                List.of("b.png", "c.png"));

        assertEquals(report.reportId(), updated.reportId());
        assertEquals(List.of("a.png", "b.png", "c.png"), updated.evidence());
        assertEquals(ReportStatus.PENDING, updated.status());
        assertEquals(NOW, updated.createdAt());
        assertNull(updated.handledAt());
        assertEquals(List.of("a.png", "b.png", "c.png"),
                reports.findById(report.reportId()).orElseThrow().getEvidence());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("the reporter can add the first evidence items to a report without evidence")
    void reporterAddsFirstEvidenceItems() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.COMMENT, 6001L);

        ReportDetail updated = service.addEvidence(report.reportId(), REPORTER_ID,
                List.of("comment-1.png"));

        assertEquals(List.of("comment-1.png"), updated.evidence());
    }

    @Test
    @DisplayName("evidence can be filled up to exactly three items")
    void evidenceCanReachExactlyThreeItems() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L, "a.png", "b.png");

        ReportDetail updated = service.addEvidence(report.reportId(), REPORTER_ID,
                List.of("c.png"));

        assertEquals(3, updated.evidence().size());
    }

    @Test
    @DisplayName("adding three items to an empty evidence list is allowed")
    void threeNewItemsOnEmptyEvidenceAreAllowed() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L);

        ReportDetail updated = service.addEvidence(report.reportId(), REPORTER_ID,
                List.of("a.png", "b.png", "c.png"));

        assertEquals(List.of("a.png", "b.png", "c.png"), updated.evidence());
    }

    @Test
    @DisplayName("a different user cannot add evidence")
    void nonReporterCannotAddEvidence() {
        ReportDetail report = submitPostReport();

        assertCode(ErrorCode.NOT_REPORTER, () -> service.addEvidence(
                report.reportId(), OTHER_REPORTER_ID, List.of("forged.png")));

        assertEquals(List.of("shot-1.png", "shot-2.png"),
                reports.findById(report.reportId()).orElseThrow().getEvidence());
    }

    @Test
    @DisplayName("a resolved report cannot receive more evidence")
    void resolvedReportRejectsNewEvidence() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L, "a.png");
        service.handle(handle(report.reportId(), true));

        assertCode(ErrorCode.REPORT_NOT_PENDING, () -> service.addEvidence(
                report.reportId(), REPORTER_ID, List.of("b.png")));

        assertEquals(1, reports.findById(report.reportId()).orElseThrow().getEvidence().size());
    }

    @Test
    @DisplayName("a rejected report cannot receive more evidence")
    void rejectedReportRejectsNewEvidence() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L, "a.png");
        service.handle(handle(report.reportId(), false));

        assertCode(ErrorCode.REPORT_NOT_PENDING, () -> service.addEvidence(
                report.reportId(), REPORTER_ID, List.of("b.png")));

        assertEquals(1, reports.findById(report.reportId()).orElseThrow().getEvidence().size());
    }

    @Test
    @DisplayName("the reporter check runs before the pending check")
    void reporterCheckRunsBeforePendingCheck() {
        ReportDetail report = submitPostReport();
        service.handle(handle(report.reportId(), true));

        assertCode(ErrorCode.NOT_REPORTER, () -> service.addEvidence(
                report.reportId(), OTHER_REPORTER_ID, List.of("late.png")));
    }

    @Test
    @DisplayName("evidence over the combined limit of three is rejected")
    void combinedEvidenceOverThreeItemsIsRejected() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L,
                "a.png", "b.png", "c.png");

        assertCode(ErrorCode.TOO_MANY_EVIDENCE, () -> service.addEvidence(
                report.reportId(), REPORTER_ID, List.of("d.png")));

        assertEquals(3, reports.findById(report.reportId()).orElseThrow().getEvidence().size());
    }

    @Test
    @DisplayName("more than three new evidence items are rejected")
    void moreThanThreeNewItemsAreRejected() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L);

        assertCode(ErrorCode.TOO_MANY_EVIDENCE, () -> service.addEvidence(
                report.reportId(), REPORTER_ID, List.of("a.png", "b.png", "c.png", "d.png")));

        assertTrue(reports.findById(report.reportId()).orElseThrow().getEvidence().isEmpty());
    }

    @Test
    @DisplayName("adding evidence to an unknown report is rejected")
    void unknownReportIsRejected() {
        assertCode(ErrorCode.REPORT_NOT_FOUND, () -> service.addEvidence(
                9999L, REPORTER_ID, List.of("a.png")));
        assertCode(ErrorCode.REPORT_NOT_FOUND, () -> service.addEvidence(
                9999L, OTHER_REPORTER_ID, List.of("a.png")));
    }

    @Test
    @DisplayName("a null evidence list adds nothing")
    void nullEvidenceAddsNothing() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.POST, 5001L, "a.png");

        ReportDetail updated = service.addEvidence(report.reportId(), REPORTER_ID, null);

        assertEquals(List.of("a.png"), updated.evidence());
    }
}
