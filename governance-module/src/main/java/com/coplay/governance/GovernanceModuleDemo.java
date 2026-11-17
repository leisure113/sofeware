package com.coplay.governance;

import com.coplay.governance.api.AdminQueryService;
import com.coplay.governance.api.ContentAdminService;
import com.coplay.governance.api.NotificationService;
import com.coplay.governance.api.TeamAdminService;
import com.coplay.governance.api.UserAdminService;
import com.coplay.governance.api.UserQueryService;
import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportBrief;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.governance.repository.InMemoryAdminLogRepository;
import com.coplay.governance.repository.InMemoryReportRepository;
import com.coplay.governance.service.GovernanceServiceImpl;
import com.coplay.governance.support.BizException;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standalone demo: submit -> duplicate guard -> handle -> target moderation -> stats.
 * Run with: java -jar target/governance-module-1.0.0.jar
 */
public final class GovernanceModuleDemo {

    private GovernanceModuleDemo() {
    }

    public static void main(String[] args) {
        DemoAdmins admins = new DemoAdmins();
        DemoUsers users = new DemoUsers();
        DemoContent content = new DemoContent();
        DemoUserAdmin userAdmin = new DemoUserAdmin();
        DemoTeamAdmin teamAdmin = new DemoTeamAdmin();
        DemoNotifications notifications = new DemoNotifications();
        InMemoryReportRepository reports = new InMemoryReportRepository();
        InMemoryAdminLogRepository adminLogs = new InMemoryAdminLogRepository();

        GovernanceServiceImpl service = new GovernanceServiceImpl(
                reports, adminLogs, admins, users, content, userAdmin, teamAdmin, notifications,
                Clock.systemDefaultZone());

        System.out.println("=== CoPlay governance module demo ===");

        ReportDetail postReport = service.submit(new SubmitReportCommand(
                2L, ReportTargetType.POST, 5001L, "Harassment",
                "Insulting comments under a post", List.of("shot-1.png", "shot-2.png")));
        printSubmitted(1, postReport);

        try {
            service.submit(new SubmitReportCommand(
                    2L, ReportTargetType.POST, 5001L, "Harassment", "", List.of()));
        } catch (BizException exception) {
            System.out.println("[2] Duplicate pending report rejected: "
                    + exception.getCode() + " / " + exception.getMessage());
        }

        ReportDetail teamReport = service.submit(new SubmitReportCommand(
                3L, ReportTargetType.TEAM, 4001L, "Cheating team",
                "Team advertised boosting", List.of("chat.png")));
        printSubmitted(3, teamReport);

        ReportDetail commentReport = service.submit(new SubmitReportCommand(
                4L, ReportTargetType.COMMENT, 6001L, "Spam comment", "", List.of()));
        printSubmitted(4, commentReport);

        ReportDetail userReport = service.submit(new SubmitReportCommand(
                1L, ReportTargetType.USER, 3001L, "Toxic behavior",
                "Abusive voice chat", List.of()));
        printSubmitted(5, userReport);

        try {
            service.submit(new SubmitReportCommand(
                    2L, ReportTargetType.COMMENT, 6002L, "Spam comment", "",
                    List.of("a.png", "b.png", "c.png", "d.png")));
        } catch (BizException exception) {
            System.out.println("[6] Too many evidence items rejected: "
                    + exception.getCode() + " / " + exception.getMessage());
        }

        try {
            service.handle(new HandleReportCommand(postReport.reportId(), 2L, true, "self"));
        } catch (BizException exception) {
            System.out.println("[7] Non-admin handle rejected: "
                    + exception.getCode() + " / " + exception.getMessage());
        }

        ReportDetail resolvedPost = service.handle(
                new HandleReportCommand(postReport.reportId(), 9L, true, "confirmed harassment"));
        System.out.println("[8] Handled #" + resolvedPost.reportId()
                + ": status=" + resolvedPost.status() + ", handler=" + resolvedPost.handlerId());

        ReportDetail resolvedTeam = service.handle(
                new HandleReportCommand(teamReport.reportId(), 9L, true, "confirmed cheating"));
        System.out.println("[9] Handled #" + resolvedTeam.reportId()
                + ": status=" + resolvedTeam.status() + ", handler=" + resolvedTeam.handlerId());

        ReportDetail rejectedComment = service.handle(
                new HandleReportCommand(commentReport.reportId(), 9L, false, "not enough evidence"));
        System.out.println("[10] Handled #" + rejectedComment.reportId()
                + ": status=" + rejectedComment.status() + ", handler=" + rejectedComment.handlerId());

        ReportDetail resolvedUser = service.handle(
                new HandleReportCommand(userReport.reportId(), 9L, true, "muted for 7 days"));
        System.out.println("[11] Handled #" + resolvedUser.reportId()
                + ": status=" + resolvedUser.status() + ", handler=" + resolvedUser.handlerId());

        try {
            service.handle(new HandleReportCommand(commentReport.reportId(), 9L, true, "again"));
        } catch (BizException exception) {
            System.out.println("[12] Re-handle rejected: "
                    + exception.getCode() + " / " + exception.getMessage());
        }

        List<ReportBrief> pending = service.listPending();
        System.out.println("[13] Pending reports: " + pending.size());

        StatsOverview stats = service.stats();
        System.out.println("[14] Stats: total=" + stats.total()
                + ", pending=" + stats.pending()
                + ", resolved=" + stats.resolved()
                + ", rejected=" + stats.rejected());
        System.out.println("     By type: POST=" + stats.byType().get(ReportTargetType.POST)
                + ", COMMENT=" + stats.byType().get(ReportTargetType.COMMENT)
                + ", USER=" + stats.byType().get(ReportTargetType.USER)
                + ", TEAM=" + stats.byType().get(ReportTargetType.TEAM));

        System.out.println("[15] Target actions:");
        content.actions().forEach(action -> System.out.println("  - " + action));
        userAdmin.actions().forEach(action -> System.out.println("  - " + action));
        teamAdmin.actions().forEach(action -> System.out.println("  - " + action));

        System.out.println("[16] Admin logs:");
        for (AdminLog log : adminLogs.findAll()) {
            System.out.println("  - logId=" + log.getLogId()
                    + ", adminId=" + log.getAdminId()
                    + ", action=" + log.getAction()
                    + ", target=" + log.getTargetType() + "#" + log.getTargetId()
                    + ", result=" + log.getResult());
        }

        System.out.println("[17] Notifications:");
        notifications.messages().forEach(message -> System.out.println("  - " + message));
    }

    private static void printSubmitted(int step, ReportDetail report) {
        System.out.println("[" + step + "] Submitted #" + report.reportId()
                + ": target=" + report.targetType() + "#" + report.targetId()
                + ", reporter=" + report.reporterId()
                + ", status=" + report.status());
    }

    private static final class DemoAdmins implements AdminQueryService {
        @Override
        public boolean isAdmin(long userId) {
            return userId == 9L;
        }
    }

    private static final class DemoUsers implements UserQueryService {
        private final Set<Long> active = Set.of(1L, 2L, 3L, 4L);

        @Override
        public boolean isActive(long userId) {
            return active.contains(userId);
        }
    }

    private static final class DemoContent implements ContentAdminService {
        private final List<String> actions = new CopyOnWriteArrayList<>();

        @Override
        public void hidePost(long postId, String reason) {
            actions.add("ContentAdminService.hidePost(" + postId + ", " + reason + ")");
        }

        @Override
        public void hideComment(long commentId, String reason) {
            actions.add("ContentAdminService.hideComment(" + commentId + ", " + reason + ")");
        }

        List<String> actions() {
            return actions;
        }
    }

    private static final class DemoUserAdmin implements UserAdminService {
        private final List<String> actions = new CopyOnWriteArrayList<>();

        @Override
        public void muteUser(long userId, String reason) {
            actions.add("UserAdminService.muteUser(" + userId + ", " + reason + ")");
        }

        @Override
        public void banUser(long userId, String reason) {
            actions.add("UserAdminService.banUser(" + userId + ", " + reason + ")");
        }

        List<String> actions() {
            return actions;
        }
    }

    private static final class DemoTeamAdmin implements TeamAdminService {
        private final List<String> actions = new CopyOnWriteArrayList<>();

        @Override
        public void closeTeam(long teamId, String reason) {
            actions.add("TeamAdminService.closeTeam(" + teamId + ", " + reason + ")");
        }

        List<String> actions() {
            return actions;
        }
    }

    private static final class DemoNotifications implements NotificationService {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void notify(long userId, String type, String content) {
            messages.add("userId=" + userId + ", type=" + type + ", content=" + content);
        }

        List<String> messages() {
            return messages;
        }
    }
}
