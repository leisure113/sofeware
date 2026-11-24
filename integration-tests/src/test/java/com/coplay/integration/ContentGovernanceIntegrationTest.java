package com.coplay.integration;

import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.integration.support.IntegrationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentGovernanceIntegrationTest {

    private IntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new IntegrationFixture();
    }

    @Test
    @DisplayName("IT-03 reported post is hidden after an administrator handles the report")
    void reportedPostIsHiddenAfterHandling() {
        long author = fixture.registerActive("11");
        long reporter = fixture.registerActive("12");
        long postId = fixture.createPost(author);

        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.POST, postId, "spam", "bad post", List.of()));
        var resolved = fixture.governanceService.handle(new HandleReportCommand(
                report.reportId(), fixture.adminId, true, "confirmed"));

        assertEquals(ReportStatus.RESOLVED, resolved.status());
        var error = assertThrows(com.coplay.content.support.BizException.class,
                () -> fixture.contentService.getPost(postId));
        assertEquals("CONTENT-404", error.getCode());
        assertTrue(fixture.notifications.countByType("CONTENT_HIDDEN") >= 1);
    }

    @Test
    @DisplayName("IT-04 reported comment is hidden and no longer returned")
    void reportedCommentIsHiddenAfterHandling() {
        long author = fixture.registerActive("13");
        long commenter = fixture.registerActive("14");
        long reporter = fixture.registerActive("15");
        long postId = fixture.createPost(author);
        long commentId = fixture.contentService.addComment(
                new CreateCommentCommand(postId, commenter, "reported comment")).commentId();

        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.COMMENT, commentId, "abuse", "", List.of()));
        fixture.governanceService.handle(new HandleReportCommand(
                report.reportId(), fixture.adminId, true, "confirmed"));

        assertTrue(fixture.contentService.listComments(postId).isEmpty());
    }

    @Test
    @DisplayName("IT-07 non-admin cannot handle a report and content stays visible")
    void nonAdminCannotHandleReport() {
        long author = fixture.registerActive("16");
        long reporter = fixture.registerActive("17");
        long postId = fixture.createPost(author);
        var report = fixture.governanceService.submit(new SubmitReportCommand(
                reporter, ReportTargetType.POST, postId, "spam", "", List.of()));

        var error = assertThrows(com.coplay.governance.support.BizException.class,
                () -> fixture.governanceService.handle(new HandleReportCommand(
                        report.reportId(), reporter, true, "not admin")));
        assertEquals("GOV-403", error.getCode());
        assertEquals(postId, fixture.contentService.getPost(postId).postId());
    }
}
