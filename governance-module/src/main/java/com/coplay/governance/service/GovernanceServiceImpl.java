package com.coplay.governance.service;

import com.coplay.governance.api.AdminQueryService;
import com.coplay.governance.api.ContentAdminService;
import com.coplay.governance.api.GovernanceService;
import com.coplay.governance.api.NotificationService;
import com.coplay.governance.api.TeamAdminService;
import com.coplay.governance.api.UserAdminService;
import com.coplay.governance.api.UserQueryService;
import com.coplay.governance.domain.AdminLog;
import com.coplay.governance.domain.Report;
import com.coplay.governance.domain.ReportStatus;
import com.coplay.governance.domain.ReportTargetType;
import com.coplay.governance.dto.HandleReportCommand;
import com.coplay.governance.dto.ReportBrief;
import com.coplay.governance.dto.ReportDetail;
import com.coplay.governance.dto.StatsOverview;
import com.coplay.governance.dto.SubmitReportCommand;
import com.coplay.governance.repository.AdminLogRepository;
import com.coplay.governance.repository.ReportRepository;
import com.coplay.governance.support.BizException;
import com.coplay.governance.support.ErrorCode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Governance module implementation.
 *
 * <p>The service only depends on ports (admin/user queries, content/user/team moderation,
 * notification, repositories), so it can be tested and run without Spring, MySQL or the
 * other modules.</p>
 */
public final class GovernanceServiceImpl implements GovernanceService {

    private static final int MIN_REASON = 2;
    private static final int MAX_REASON = 100;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_EVIDENCE = 3;
    private static final String NOTIFICATION_TYPE = "REPORT_RESULT";
    private static final String LOG_ACTION = "HANDLE_REPORT";

    private final ReportRepository reportRepository;
    private final AdminLogRepository adminLogRepository;
    private final AdminQueryService adminQueryService;
    private final UserQueryService userQueryService;
    private final ContentAdminService contentAdminService;
    private final UserAdminService userAdminService;
    private final TeamAdminService teamAdminService;
    private final NotificationService notificationService;
    private final Clock clock;

    public GovernanceServiceImpl(ReportRepository reportRepository,
                                 AdminLogRepository adminLogRepository,
                                 AdminQueryService adminQueryService,
                                 UserQueryService userQueryService,
                                 ContentAdminService contentAdminService,
                                 UserAdminService userAdminService,
                                 TeamAdminService teamAdminService,
                                 NotificationService notificationService,
                                 Clock clock) {
        this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository");
        this.adminLogRepository = Objects.requireNonNull(adminLogRepository, "adminLogRepository");
        this.adminQueryService = Objects.requireNonNull(adminQueryService, "adminQueryService");
        this.userQueryService = Objects.requireNonNull(userQueryService, "userQueryService");
        this.contentAdminService = Objects.requireNonNull(contentAdminService, "contentAdminService");
        this.userAdminService = Objects.requireNonNull(userAdminService, "userAdminService");
        this.teamAdminService = Objects.requireNonNull(teamAdminService, "teamAdminService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ReportDetail submit(SubmitReportCommand command) {
        Objects.requireNonNull(command, "command");
        requireActiveReporter(command.reporterId());
        validateTarget(command.targetType(), command.targetId());
        String reason = normalizeReason(command.reason());
        String description = normalizeDescription(command.description());
        List<String> evidence = normalizeEvidence(command.evidence());
        ensureNoDuplicatePending(command);

        Report report = new Report(
                reportRepository.nextId(),
                command.reporterId(),
                command.targetType(),
                command.targetId(),
                reason,
                description,
                evidence,
                now());
        reportRepository.save(report);
        return toDetail(report);
    }

    @Override
    public ReportDetail handle(HandleReportCommand command) {
        Objects.requireNonNull(command, "command");
        requireAdmin(command.operatorId());
        Report report = requireReport(command.reportId());
        if (!report.isPending()) {
            throw new BizException(ErrorCode.REPORT_NOT_PENDING);
        }

        String note = normalizeNote(command.note());
        LocalDateTime handledAt = now();
        if (command.valid()) {
            applyTargetAction(report, note);
            report.resolve(command.operatorId(), note);
        } else {
            report.reject(command.operatorId(), note);
        }
        report.markHandledAt(handledAt);
        reportRepository.save(report);
        saveAdminLog(report, handledAt);
        notifyReporter(report);
        return toDetail(report);
    }

    @Override
    public ReportDetail addEvidence(long reportId, long reporterId, List<String> evidence) {
        Report report = requireReport(reportId);
        if (report.getReporterId() != reporterId) {
            throw new BizException(ErrorCode.NOT_REPORTER);
        }
        if (!report.isPending()) {
            throw new BizException(ErrorCode.REPORT_NOT_PENDING);
        }
        List<String> additions = normalizeEvidence(evidence);
        if (report.getEvidence().size() + additions.size() > MAX_EVIDENCE) {
            throw new BizException(ErrorCode.TOO_MANY_EVIDENCE);
        }
        report.appendEvidence(additions);
        reportRepository.save(report);
        return toDetail(report);
    }

    @Override
    public List<ReportBrief> listPending() {
        return reportRepository.findPending().stream()
                .map(this::toBrief)
                .toList();
    }

    @Override
    public StatsOverview stats() {
        List<Report> all = reportRepository.findAll();
        Map<ReportTargetType, Long> byType = new EnumMap<>(ReportTargetType.class);
        for (ReportTargetType type : ReportTargetType.values()) {
            byType.put(type, 0L);
        }
        for (Report report : all) {
            byType.merge(report.getTargetType(), 1L, Long::sum);
        }
        return new StatsOverview(
                all.size(),
                reportRepository.countByStatus(ReportStatus.PENDING),
                reportRepository.countByStatus(ReportStatus.RESOLVED),
                reportRepository.countByStatus(ReportStatus.REJECTED),
                byType);
    }

    private void requireActiveReporter(long reporterId) {
        if (reporterId <= 0) {
            throw new BizException(ErrorCode.INVALID_OPERATOR);
        }
        if (!userQueryService.isActive(reporterId)) {
            throw new BizException(ErrorCode.INVALID_OPERATOR, "reporter " + reporterId + " is not active");
        }
    }

    private void requireAdmin(long operatorId) {
        if (operatorId <= 0) {
            throw new BizException(ErrorCode.INVALID_OPERATOR);
        }
        if (!adminQueryService.isAdmin(operatorId)) {
            throw new BizException(ErrorCode.NOT_ADMIN);
        }
    }

    private Report requireReport(long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BizException(ErrorCode.REPORT_NOT_FOUND));
    }

    private void validateTarget(ReportTargetType targetType, long targetId) {
        if (targetType == null || targetId <= 0) {
            throw new BizException(ErrorCode.INVALID_TARGET);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new BizException(ErrorCode.INVALID_REASON);
        }
        String trimmed = reason.trim();
        if (trimmed.length() < MIN_REASON || trimmed.length() > MAX_REASON) {
            throw new BizException(ErrorCode.INVALID_REASON,
                    "reason length must be between " + MIN_REASON + " and " + MAX_REASON);
        }
        return trimmed;
    }

    private String normalizeDescription(String description) {
        String trimmed = Objects.requireNonNullElse(description, "").trim();
        if (trimmed.length() > MAX_DESCRIPTION) {
            throw new BizException(ErrorCode.INVALID_REASON,
                    "description must be at most " + MAX_DESCRIPTION + " characters");
        }
        return trimmed;
    }

    private List<String> normalizeEvidence(List<String> evidence) {
        List<String> copy = evidence == null ? List.of() : List.copyOf(evidence);
        if (copy.size() > MAX_EVIDENCE) {
            throw new BizException(ErrorCode.TOO_MANY_EVIDENCE);
        }
        return copy;
    }

    private String normalizeNote(String note) {
        return Objects.requireNonNullElse(note, "").trim();
    }

    private void ensureNoDuplicatePending(SubmitReportCommand command) {
        boolean duplicate = reportRepository.findPending().stream()
                .anyMatch(report -> report.getReporterId() == command.reporterId()
                        && report.getTargetType() == command.targetType()
                        && report.getTargetId() == command.targetId());
        if (duplicate) {
            throw new BizException(ErrorCode.DUPLICATE_REPORT);
        }
    }

    private void applyTargetAction(Report report, String note) {
        String actionReason = actionReason(report, note);
        try {
            switch (report.getTargetType()) {
                case POST -> contentAdminService.hidePost(report.getTargetId(), actionReason);
                case COMMENT -> contentAdminService.hideComment(report.getTargetId(), actionReason);
                case USER -> userAdminService.muteUser(report.getTargetId(), actionReason);
                case TEAM -> teamAdminService.closeTeam(report.getTargetId(), actionReason);
                default -> throw new IllegalStateException(
                        "Unsupported target type: " + report.getTargetType());
            }
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.TARGET_ACTION_FAILED,
                    report.getTargetType() + "#" + report.getTargetId()
                            + " (" + exception.getMessage() + ")");
        }
    }

    private String actionReason(Report report, String note) {
        String base = "Report " + report.getId() + ": " + report.getReason();
        return note.isBlank() ? base : base + " (" + note + ")";
    }

    private void saveAdminLog(Report report, LocalDateTime handledAt) {
        adminLogRepository.save(new AdminLog(
                adminLogRepository.nextId(),
                report.getHandlerId(),
                LOG_ACTION,
                report.getTargetType(),
                report.getTargetId(),
                report.getStatus().name(),
                handledAt));
    }

    private void notifyReporter(Report report) {
        String outcome = report.getStatus() == ReportStatus.RESOLVED ? "resolved" : "rejected";
        String content = "Your report #" + report.getId() + " was " + outcome;
        if (!report.getHandleNote().isBlank()) {
            content = content + ": " + report.getHandleNote();
        }
        notificationService.notify(report.getReporterId(), NOTIFICATION_TYPE, content);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private ReportBrief toBrief(Report report) {
        return new ReportBrief(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt());
    }

    private ReportDetail toDetail(Report report) {
        return new ReportDetail(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getEvidence(),
                report.getStatus(),
                report.getHandlerId(),
                report.getHandleNote(),
                report.getCreatedAt(),
                report.getHandledAt());
    }
}
