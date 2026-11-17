package com.coplay.governance;

import com.coplay.governance.api.ContentAdminService;
import com.coplay.governance.api.TeamAdminService;
import com.coplay.governance.api.UserAdminService;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportDetail;
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
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared fixture for governance tests.
 *
 * <p>Every test gets fresh in-memory repositories, fresh port fakes and a service wired
 * with a fixed clock, so the tests are deterministic and independent.</p>
 */
abstract class GovernanceTestSupport {

    protected static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC);
    protected static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    protected static final long ADMIN_ID = 9L;
    protected static final long REPORTER_ID = 2L;
    protected static final long OTHER_REPORTER_ID = 3L;

    protected InMemoryReportRepository reports;
    protected InMemoryAdminLogRepository adminLogs;
    protected FakeAdminQueryService admins;
    protected FakeUserQueryService users;
    protected FakeContentAdminService content;
    protected FakeUserAdminService userAdmins;
    protected FakeTeamAdminService teams;
    protected FakeNotificationService notifications;
    protected GovernanceServiceImpl service;

    @BeforeEach
    void resetFixture() {
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

    protected GovernanceServiceImpl newService(ContentAdminService contentPort,
                                               UserAdminService userAdminPort,
                                               TeamAdminService teamAdminPort) {
        return new GovernanceServiceImpl(
                reports, adminLogs, admins, users, contentPort, userAdminPort, teamAdminPort,
                notifications, CLOCK);
    }

    protected SubmitReportCommand postReport() {
        return new SubmitReportCommand(
                REPORTER_ID, ReportTargetType.POST, 5001L, "Harassment",
                "Insulting comments under a post", List.of("shot-1.png", "shot-2.png"));
    }

    protected ReportDetail submitPostReport() {
        return service.submit(postReport());
    }

    protected ReportDetail submit(ReportTargetType targetType, long targetId) {
        return submit(REPORTER_ID, targetType, targetId);
    }

    protected ReportDetail submit(long reporterId, ReportTargetType targetType, long targetId,
                                  String... evidence) {
        return service.submit(new SubmitReportCommand(
                reporterId, targetType, targetId, "Harassment", "", List.of(evidence)));
    }

    protected HandleReportCommand handle(long reportId, boolean valid) {
        return new HandleReportCommand(reportId, ADMIN_ID, valid, "checked evidence");
    }

    protected static void assertCode(ErrorCode expected, BizException actual) {
        assertEquals(expected.getCode(), actual.getCode());
    }

    protected static void assertCode(ErrorCode expected, Executable executable) {
        assertCode(expected, assertThrows(BizException.class, executable));
    }
}
