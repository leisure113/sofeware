package com.coplay.governance;

import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportBrief;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.governance.fake.FakeAdminQueryService;
import com.coplay.governance.fake.FakeContentAdminService;
import com.coplay.governance.fake.FakeNotificationService;
import com.coplay.governance.fake.FakeTeamAdminService;
import com.coplay.governance.fake.FakeUserAdminService;
import com.coplay.governance.fake.FakeUserQueryService;
import com.coplay.governance.repository.InMemoryAdminLogRepository;
import com.coplay.governance.repository.InMemoryReportRepository;
import com.coplay.governance.service.GovernanceServiceImpl;
import com.coplay.governance.support.BizException;
import com.coplay.governance.support.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final long ADMIN_ID = 9L;

    private InMemoryReportRepository reports;
    private InMemoryAdminLogRepository adminLogs;
    private FakeAdminQueryService admins;
    private FakeUserQueryService users;
    private FakeContentAdminService content;
    private FakeUserAdminService userAdmins;
    private FakeTeamAdminService teams;
    private FakeNotificationService notifications;
    private GovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        reports = new InMemoryReportRepository();
        adminLogs = new InMemoryAdminLogRepository();
        admins = new FakeAdminQueryService();
        users = new FakeUserQueryService();
        content = new FakeContentAdminService();
        userAdmins = new FakeUserAdminService();
        teams = new FakeTeamAdminService();
        notifications = new FakeNotificationService();
        service = new GovernanceServiceImpl(
                reports, adminLogs, admins, users, content, userAdmins, teams, notifications, CLOCK);
    }

    @Test
    @DisplayName("submit creates a pending report with normalized fields and fixed-clock time")
    void submitCreatesPendingReport() {
        ReportDetail report = submitPostReport();

        assertTrue(report.reportId() > 0);
        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals(2L, report.reporterId());
        assertEquals(ReportTargetType.POST, report.targetType());
        assertEquals(5001L, report.targetId());
        assertEquals("Harassment", report.reason());
        assertEquals("Insulting comments under a post", report.description());
        assertEquals(List.of("shot-1.png", "shot-2.png"), report.evidence());
        assertNull(report.handlerId());
        assertTrue(report.handleNote().isEmpty());
        assertEquals(NOW, report.createdAt());
        assertNull(report.handledAt());
    }

    @Test
    @DisplayName("a second pending report for the same reporter and target is rejected")
    void duplicatePendingReportRejected() {
        ReportDetail first = submitPostReport();

        BizException exception = assertThrows(BizException.class, () -> service.submit(postReport()));

        assertCode(ErrorCode.DUPLICATE_REPORT, exception);
        assertEquals(1, reports.findAll().size());
        assertEquals(ReportStatus.PENDING, reports.findById(first.reportId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("submit rejects more than three evidence items and invalid targets")
    void submitRejectsTooManyEvidenceAndInvalidTarget() {
        BizException tooManyEvidence = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 5001L, "Harassment", "",
                        List.of("a.png", "b.png", "c.png", "d.png"))));
        assertCode(ErrorCode.TOO_MANY_EVIDENCE, tooManyEvidence);

        BizException invalidId = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 0L, "Harassment", "", List.of())));
        assertCode(ErrorCode.INVALID_TARGET, invalidId);

        BizException missingType = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, null, 5001L, "Harassment", "", List.of())));
        assertCode(ErrorCode.INVALID_TARGET, missingType);

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("submit rejects a reason outside 2..100 characters and a description over 500")
    void submitRejectsInvalidReason() {
        BizException tooShort = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 5001L, "x", "", List.of())));
        assertCode(ErrorCode.INVALID_REASON, tooShort);

        BizException tooLong = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 5001L, "a".repeat(101), "", List.of())));
        assertCode(ErrorCode.INVALID_REASON, tooLong);

        BizException missingReason = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 5001L, null, "", List.of())));
        assertCode(ErrorCode.INVALID_REASON, missingReason);

        BizException longDescription = assertThrows(BizException.class,
                () -> service.submit(new SubmitReportCommand(
                        2L, ReportTargetType.POST, 5001L, "Harassment", "a".repeat(501), List.of())));
        assertCode(ErrorCode.INVALID_REASON, longDescription);

        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    @DisplayName("a non-admin operator cannot handle a report")
    void nonAdminCannotHandle() {
        ReportDetail report = submitPostReport();

        BizException exception = assertThrows(BizException.class,
                () -> service.handle(new HandleReportCommand(report.reportId(), 2L, true, "self")));
        assertCode(ErrorCode.NOT_ADMIN, exception);

        BizException invalidOperator = assertThrows(BizException.class,
                () -> service.handle(new HandleReportCommand(report.reportId(), 0L, true, "none")));
        assertCode(ErrorCode.INVALID_OPERATOR, invalidOperator);

        assertEquals(ReportStatus.PENDING, reports.findById(report.reportId()).orElseThrow().getStatus());
        assertTrue(content.actions().isEmpty());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("a valid POST report is resolved and the content admin hides the post")
    void validPostReportHidesPostAndResolves() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(handleCommand(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(Long.valueOf(ADMIN_ID), handled.handlerId());
        assertEquals("checked evidence", handled.handleNote());
        assertEquals(NOW, handled.handledAt());
        assertEquals(1, content.countByType("HIDE_POST"));
        assertEquals(5001L, content.actions().get(0).targetId());
        assertTrue(content.actions().get(0).reason().contains("Harassment"));

        List<AdminLog> logs = adminLogs.findAll();
        assertEquals(1, logs.size());
        AdminLog log = logs.get(0);
        assertEquals(ADMIN_ID, log.getAdminId());
        assertEquals("HANDLE_REPORT", log.getAction());
        assertEquals(ReportTargetType.POST, log.getTargetType());
        assertEquals(5001L, log.getTargetId());
        assertEquals("RESOLVED", log.getResult());
        assertEquals(NOW, log.getCreatedAt());

        assertEquals(1, notifications.notifications().size());
        assertEquals(2L, notifications.notifications().get(0).userId());
        assertEquals("REPORT_RESULT", notifications.notifications().get(0).type());
        assertTrue(notifications.notifications().get(0).content().contains("resolved"));
    }

    @Test
    @DisplayName("a valid USER report mutes the user and never bans them")
    void validUserReportMutesUser() {
        ReportDetail report = service.submit(new SubmitReportCommand(
                3L, ReportTargetType.USER, 3001L, "Toxic behavior", "Abusive voice chat", List.of()));

        ReportDetail handled = service.handle(handleCommand(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(List.of(3001L), userAdmins.mutedUserIds());
        assertEquals(0, userAdmins.countByType("BAN_USER"));
        assertTrue(content.actions().isEmpty());
        assertTrue(teams.actions().isEmpty());
    }

    @Test
    @DisplayName("an invalid report is rejected without touching the target")
    void invalidReportIsRejectedWithoutTargetAction() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(handleCommand(report.reportId(), false));

        assertEquals(ReportStatus.REJECTED, handled.status());
        assertEquals(Long.valueOf(ADMIN_ID), handled.handlerId());
        assertEquals(NOW, handled.handledAt());
        assertTrue(content.actions().isEmpty());
        assertTrue(userAdmins.actions().isEmpty());
        assertTrue(teams.actions().isEmpty());
        assertEquals(1, adminLogs.findAll().size());
        assertEquals("REJECTED", adminLogs.findAll().get(0).getResult());
        assertEquals(1, notifications.notifications().size());
        assertTrue(notifications.notifications().get(0).content().contains("rejected"));
    }

    @Test
    @DisplayName("an already handled or unknown report cannot be handled again")
    void alreadyHandledReportCannotBeHandledAgain() {
        ReportDetail report = submitPostReport();
        service.handle(handleCommand(report.reportId(), true));

        BizException notPending = assertThrows(BizException.class,
                () -> service.handle(handleCommand(report.reportId(), false)));
        assertCode(ErrorCode.REPORT_NOT_PENDING, notPending);

        BizException notFound = assertThrows(BizException.class,
                () -> service.handle(handleCommand(9999L, true)));
        assertCode(ErrorCode.REPORT_NOT_FOUND, notFound);

        assertEquals(1, adminLogs.findAll().size());
        assertEquals(1, content.countByType("HIDE_POST"));
    }

    @Test
    @DisplayName("resolving and rejecting both notify the reporter with REPORT_RESULT")
    void resolvedAndRejectedReportsNotifyReporter() {
        ReportDetail resolved = submitPostReport();
        service.handle(handleCommand(resolved.reportId(), true));

        ReportDetail rejected = service.submit(new SubmitReportCommand(
                3L, ReportTargetType.TEAM, 4001L, "Cheating", "Screenshot attached", List.of("proof.png")));
        service.handle(new HandleReportCommand(rejected.reportId(), ADMIN_ID, false, "not enough evidence"));

        List<FakeNotificationService.Notification> sent = notifications.notifications();
        assertEquals(2, sent.size());
        assertEquals(2, notifications.countByType("REPORT_RESULT"));

        assertEquals(2L, sent.get(0).userId());
        assertTrue(sent.get(0).content().contains("#" + resolved.reportId()));
        assertTrue(sent.get(0).content().contains("resolved"));

        assertEquals(3L, sent.get(1).userId());
        assertTrue(sent.get(1).content().contains("#" + rejected.reportId()));
        assertTrue(sent.get(1).content().contains("rejected"));
    }

    @Test
    @DisplayName("stats counts reports by status and by target type")
    void statsCountsByStatusAndType() {
        ReportDetail postOne = service.submit(new SubmitReportCommand(
                2L, ReportTargetType.POST, 5001L, "Harassment", "", List.of()));
        ReportDetail postTwo = service.submit(new SubmitReportCommand(
                3L, ReportTargetType.POST, 5002L, "Spam post", "", List.of()));
        ReportDetail userReport = service.submit(new SubmitReportCommand(
                1L, ReportTargetType.USER, 3001L, "Toxic user", "", List.of()));
        ReportDetail teamReport = service.submit(new SubmitReportCommand(
                4L, ReportTargetType.TEAM, 4001L, "Cheating team", "", List.of()));

        service.handle(handleCommand(postOne.reportId(), true));
        service.handle(handleCommand(userReport.reportId(), false));

        StatsOverview stats = service.stats();
        assertEquals(4L, stats.total());
        assertEquals(2L, stats.pending());
        assertEquals(1L, stats.resolved());
        assertEquals(1L, stats.rejected());
        assertEquals(Long.valueOf(2L), stats.byType().get(ReportTargetType.POST));
        assertEquals(Long.valueOf(0L), stats.byType().get(ReportTargetType.COMMENT));
        assertEquals(Long.valueOf(1L), stats.byType().get(ReportTargetType.USER));
        assertEquals(Long.valueOf(1L), stats.byType().get(ReportTargetType.TEAM));
        assertEquals(Set.of(ReportTargetType.values()), stats.byType().keySet());

        List<ReportBrief> pending = service.listPending();
        assertEquals(List.of(teamReport.reportId(), postTwo.reportId()),
                pending.stream().map(ReportBrief::reportId).toList());
        assertTrue(pending.stream().allMatch(brief -> brief.status() == ReportStatus.PENDING));
    }

    @Test
    @DisplayName("a failing target action fails the handle and keeps the report pending")
    void targetActionFailureKeepsReportPending() {
        ReportDetail report = submitPostReport();
        content.failWith("content service unavailable");

        BizException exception = assertThrows(BizException.class,
                () -> service.handle(handleCommand(report.reportId(), true)));

        assertCode(ErrorCode.TARGET_ACTION_FAILED, exception);
        assertEquals(ReportStatus.PENDING, reports.findById(report.reportId()).orElseThrow().getStatus());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

    private SubmitReportCommand postReport() {
        return new SubmitReportCommand(
                2L, ReportTargetType.POST, 5001L, "Harassment",
                "Insulting comments under a post", List.of("shot-1.png", "shot-2.png"));
    }

    private ReportDetail submitPostReport() {
        return service.submit(postReport());
    }

    private HandleReportCommand handleCommand(long reportId, boolean valid) {
        return new HandleReportCommand(reportId, ADMIN_ID, valid, "checked evidence");
    }

    private static void assertCode(ErrorCode expected, BizException exception) {
        assertEquals(expected.getCode(), exception.getCode());
    }
}
