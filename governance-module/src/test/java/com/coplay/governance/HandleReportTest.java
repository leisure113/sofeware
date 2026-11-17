package com.coplay.governance;

import com.coplay.governance.api.ContentAdminService;
import com.coplay.governance.api.TeamAdminService;
import com.coplay.governance.api.UserAdminService;
import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.fake.FakeNotificationService;
import com.coplay.governance.service.GovernanceServiceImpl;
import com.coplay.governance.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * handle(): authorization, state machine, target dispatching and side effects.
 */
class HandleReportTest extends GovernanceTestSupport {

    @Test
    @DisplayName("a non-admin operator is rejected before the report is loaded")
    void nonAdminOperatorIsRejected() {
        ReportDetail report = submitPostReport();

        assertCode(ErrorCode.NOT_ADMIN,
                () -> service.handle(new HandleReportCommand(report.reportId(), 2L, true, "self")));
        assertCode(ErrorCode.NOT_ADMIN,
                () -> service.handle(new HandleReportCommand(9999L, 2L, true, "self")));

        assertEquals(ReportStatus.PENDING, reports.findById(report.reportId()).orElseThrow().getStatus());
        assertTrue(content.actions().isEmpty());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("a non-positive operator id is rejected before the report is loaded")
    void invalidOperatorIsRejected() {
        ReportDetail report = submitPostReport();

        assertCode(ErrorCode.INVALID_OPERATOR,
                () -> service.handle(new HandleReportCommand(report.reportId(), 0L, true, "none")));
        assertCode(ErrorCode.INVALID_OPERATOR,
                () -> service.handle(new HandleReportCommand(report.reportId(), -1L, true, "none")));
        assertCode(ErrorCode.INVALID_OPERATOR,
                () -> service.handle(new HandleReportCommand(9999L, 0L, true, "none")));

        assertEquals(ReportStatus.PENDING, reports.findById(report.reportId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("handling an unknown report fails with REPORT_NOT_FOUND")
    void unknownReportIsRejected() {
        assertCode(ErrorCode.REPORT_NOT_FOUND, () -> service.handle(handle(9999L, true)));

        assertTrue(content.actions().isEmpty());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("a resolved report cannot be handled again")
    void resolvedReportCannotBeHandledAgain() {
        ReportDetail report = submitPostReport();
        service.handle(handle(report.reportId(), true));

        assertCode(ErrorCode.REPORT_NOT_PENDING, () -> service.handle(handle(report.reportId(), false)));

        assertEquals(1, adminLogs.findAll().size());
        assertEquals(1, notifications.notifications().size());
    }

    @Test
    @DisplayName("a rejected report cannot be handled again")
    void rejectedReportCannotBeHandledAgain() {
        ReportDetail report = submitPostReport();
        service.handle(handle(report.reportId(), false));

        assertCode(ErrorCode.REPORT_NOT_PENDING, () -> service.handle(handle(report.reportId(), true)));

        assertEquals(1, adminLogs.findAll().size());
        assertEquals(1, notifications.notifications().size());
    }

    @Test
    @DisplayName("valid=false rejects the report, logs and notifies without touching the target")
    void invalidReportIsRejectedWithoutTargetAction() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(new HandleReportCommand(
                report.reportId(), ADMIN_ID, false, "not enough evidence"));

        assertEquals(ReportStatus.REJECTED, handled.status());
        assertEquals(Long.valueOf(ADMIN_ID), handled.handlerId());
        assertEquals("not enough evidence", handled.handleNote());
        assertEquals(NOW, handled.handledAt());

        assertTrue(content.actions().isEmpty());
        assertTrue(userAdmins.actions().isEmpty());
        assertTrue(teams.actions().isEmpty());
        assertEquals(1, adminLogs.findAll().size());
        assertEquals("REJECTED", adminLogs.findAll().get(0).getResult());
        assertEquals(1, notifications.notifications().size());
    }

    @Test
    @DisplayName("valid=true for POST hides the post and resolves the report")
    void validPostReportHidesPost() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(handle(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(1, content.countByType("HIDE_POST"));
        assertEquals(5001L, content.actions().get(0).targetId());
        assertTrue(content.actions().get(0).reason().contains("Harassment"));
        assertTrue(content.actions().get(0).reason().contains("checked evidence"));
        assertTrue(teams.actions().isEmpty());
        assertTrue(userAdmins.actions().isEmpty());
    }

    @Test
    @DisplayName("valid=true for COMMENT hides the comment and resolves the report")
    void validCommentReportHidesComment() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.COMMENT, 6001L);

        ReportDetail handled = service.handle(handle(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(1, content.countByType("HIDE_COMMENT"));
        assertEquals(6001L, content.actions().get(0).targetId());
        assertEquals(0, content.countByType("HIDE_POST"));
        assertTrue(teams.actions().isEmpty());
        assertTrue(userAdmins.actions().isEmpty());
    }

    @Test
    @DisplayName("valid=true for USER mutes the user and never bans them")
    void validUserReportMutesUser() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.USER, 3001L);

        ReportDetail handled = service.handle(handle(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(List.of(3001L), userAdmins.mutedUserIds());
        assertEquals(0, userAdmins.countByType("BAN_USER"));
        assertTrue(content.actions().isEmpty());
        assertTrue(teams.actions().isEmpty());
    }

    @Test
    @DisplayName("valid=true for TEAM closes the team and resolves the report")
    void validTeamReportClosesTeam() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L);

        ReportDetail handled = service.handle(handle(report.reportId(), true));

        assertEquals(ReportStatus.RESOLVED, handled.status());
        assertEquals(List.of(4001L), teams.closedTeamIds());
        assertEquals("CLOSE_TEAM", teams.actions().get(0).type());
        assertTrue(content.actions().isEmpty());
        assertTrue(userAdmins.actions().isEmpty());
    }

    @Test
    @DisplayName("a POST target action failure fails the handle and leaves the report pending")
    void postTargetActionFailureKeepsReportPending() {
        ReportDetail report = submitPostReport();
        content.failWith("content service unavailable");

        assertTargetActionFailure(report.reportId(), service);
    }

    @Test
    @DisplayName("a COMMENT target action failure fails the handle and leaves the report pending")
    void commentTargetActionFailureKeepsReportPending() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.COMMENT, 6001L);
        content.failWith("content service unavailable");

        assertTargetActionFailure(report.reportId(), service);
    }

    @Test
    @DisplayName("a USER target action failure fails the handle and leaves the report pending")
    void userTargetActionFailureKeepsReportPending() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.USER, 3001L);
        UserAdminService failing = new UserAdminService() {
            @Override
            public void muteUser(long userId, String reason) {
                throw new IllegalStateException("user service unavailable");
            }

            @Override
            public void banUser(long userId, String reason) {
                throw new IllegalStateException("user service unavailable");
            }
        };

        assertTargetActionFailure(report.reportId(), newService(content, failing, teams));
        assertTrue(userAdmins.actions().isEmpty());
    }

    @Test
    @DisplayName("a TEAM target action failure fails the handle and leaves the report pending")
    void teamTargetActionFailureKeepsReportPending() {
        ReportDetail report = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L);
        TeamAdminService failing = new TeamAdminService() {
            @Override
            public void closeTeam(long teamId, String reason) {
                throw new IllegalStateException("team service unavailable");
            }
        };

        assertTargetActionFailure(report.reportId(), newService(content, userAdmins, failing));
        assertTrue(teams.actions().isEmpty());
    }

    @Test
    @DisplayName("the target action runs while the report is still PENDING")
    void targetActionRunsWhileReportIsStillPending() {
        ReportDetail report = submitPostReport();
        long reportId = report.reportId();
        AtomicReference<ReportStatus> statusDuringAction = new AtomicReference<>();
        ContentAdminService observing = new ContentAdminService() {
            @Override
            public void hidePost(long postId, String reason) {
                statusDuringAction.set(reports.findById(reportId).orElseThrow().getStatus());
            }

            @Override
            public void hideComment(long commentId, String reason) {
                statusDuringAction.set(reports.findById(reportId).orElseThrow().getStatus());
            }
        };

        ReportDetail handled = newService(observing, userAdmins, teams)
                .handle(handle(reportId, true));

        assertEquals(ReportStatus.PENDING, statusDuringAction.get());
        assertEquals(ReportStatus.RESOLVED, handled.status());
    }

    @Test
    @DisplayName("the target action reason contains the report id, reason and trimmed note")
    void targetActionReasonContainsReportContext() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(new HandleReportCommand(
                report.reportId(), ADMIN_ID, true, "  checked  "));

        assertEquals("checked", handled.handleNote());
        assertEquals("Report " + report.reportId() + ": Harassment (checked)",
                content.actions().get(0).reason());
        assertEquals("Your report #" + report.reportId() + " was resolved: checked",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("a blank note is omitted from the action reason and the notification")
    void blankNoteIsOmitted() {
        ReportDetail report = submitPostReport();

        ReportDetail handled = service.handle(new HandleReportCommand(
                report.reportId(), ADMIN_ID, true, null));

        assertTrue(handled.handleNote().isEmpty());
        assertEquals("Report " + report.reportId() + ": Harassment",
                content.actions().get(0).reason());
        assertFalse(notifications.notifications().get(0).content().contains(":"));
        assertEquals("Your report #" + report.reportId() + " was resolved",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("every handled report writes one audit log with the resulting status")
    void everyHandleWritesOneAuditLog() {
        ReportDetail resolvedReport = submitPostReport();
        ReportDetail rejectedReport = submit(REPORTER_ID, ReportTargetType.TEAM, 4001L);

        service.handle(new HandleReportCommand(resolvedReport.reportId(), ADMIN_ID, true, "ok"));
        service.handle(new HandleReportCommand(rejectedReport.reportId(), ADMIN_ID, false, "no"));

        List<AdminLog> logs = adminLogs.findAll();
        assertEquals(2, logs.size());

        AdminLog resolvedLog = logs.get(0);
        assertEquals(ADMIN_ID, resolvedLog.getAdminId());
        assertEquals("HANDLE_REPORT", resolvedLog.getAction());
        assertEquals(ReportTargetType.POST, resolvedLog.getTargetType());
        assertEquals(5001L, resolvedLog.getTargetId());
        assertEquals("RESOLVED", resolvedLog.getResult());
        assertEquals(NOW, resolvedLog.getCreatedAt());

        AdminLog rejectedLog = logs.get(1);
        assertEquals(ReportTargetType.TEAM, rejectedLog.getTargetType());
        assertEquals(4001L, rejectedLog.getTargetId());
        assertEquals("REJECTED", rejectedLog.getResult());
        assertTrue(rejectedLog.getLogId() > resolvedLog.getLogId());
    }

    @Test
    @DisplayName("only the reporter receives the REPORT_RESULT notification")
    void notificationGoesToTheReporter() {
        ReportDetail report = submit(OTHER_REPORTER_ID, ReportTargetType.POST, 5001L);

        service.handle(new HandleReportCommand(report.reportId(), ADMIN_ID, false, "no"));

        List<FakeNotificationService.Notification> sent = notifications.notifications();
        assertEquals(1, sent.size());
        assertEquals(OTHER_REPORTER_ID, sent.get(0).userId());
        assertEquals("REPORT_RESULT", sent.get(0).type());
        assertEquals(1, notifications.countByType("REPORT_RESULT"));
    }

    @Test
    @DisplayName("a null handle command is rejected")
    void nullHandleCommandIsRejected() {
        assertThrows(NullPointerException.class, () -> service.handle(null));
    }

    private void assertTargetActionFailure(long reportId, GovernanceServiceImpl invokingService) {
        assertCode(ErrorCode.TARGET_ACTION_FAILED, () -> invokingService.handle(handle(reportId, true)));

        Report stored = reports.findById(reportId).orElseThrow();
        assertEquals(ReportStatus.PENDING, stored.getStatus());
        assertNull(stored.getHandlerId());
        assertEquals("", stored.getHandleNote());
        assertNull(stored.getHandledAt());
        assertTrue(adminLogs.findAll().isEmpty());
        assertTrue(notifications.notifications().isEmpty());
    }

}
