package com.coplay.integration;

import com.coplay.governance.api.ContentAdminService;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.governance.service.GovernanceServiceImpl;
import com.coplay.integration.support.IntegrationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailurePropagationIntegrationTest {

    private IntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntegrationFixture();
    }

    @Test
    @DisplayName("IT-08 target moderation failure keeps the report pending and skips audit/notification")
    void targetFailureKeepsReportPending() {
        ContentAdminService failingContentAdmin = new ContentAdminService() {
            @Override
            public void hidePost(long postId, String reason) {
                throw new IllegalStateException("content service unavailable");
            }

            @Override
            public void hideComment(long commentId, String reason) {
                throw new IllegalStateException("content service unavailable");
            }
        };
        var governance = new GovernanceServiceImpl(
                fixture.reportRepository,
                fixture.adminLogRepository,
                fixture.adminQuery,
                fixture.userQuery,
                failingContentAdmin,
                fixture.userAdmin,
                fixture.teamAdmin,
                fixture.notifications,
                IntegrationFixture.CLOCK);

        long author = fixture.registerActive("31");
        long reporter = fixture.registerActive("32");
        long postId = fixture.createPost(author);
        var report = governance.submit(new SubmitReportCommand(
                reporter, ReportTargetType.POST, postId, "spam", "", List.of()));

        var error = assertThrows(com.coplay.governance.support.BizException.class,
                () -> governance.handle(new HandleReportCommand(
                        report.reportId(), fixture.adminId, true, "try hide")));

        assertEquals("GOV-502", error.getCode());
        assertEquals(1, governance.listPending().size());
        assertTrue(fixture.adminLogRepository.findAll().isEmpty());
        assertTrue(fixture.notifications.all().isEmpty());
        assertEquals(postId, fixture.contentService.getPost(postId).postId());
    }
}
