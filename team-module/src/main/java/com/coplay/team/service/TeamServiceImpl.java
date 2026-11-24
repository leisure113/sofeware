package com.coplay.team.service;

import com.coplay.team.api.GameQueryService;
import com.coplay.team.api.NotificationService;
import com.coplay.team.api.TeamService;
import com.coplay.team.api.UserQueryService;
import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.Team;
import com.coplay.team.domain.TeamApplication;
import com.coplay.team.domain.TeamStatus;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.ApplicationBrief;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamBrief;
import com.coplay.team.dto.TeamDetail;
import com.coplay.team.repository.TeamRepository;
import com.coplay.team.support.BizException;
import com.coplay.team.support.ErrorCode;
import com.coplay.team.support.TeamLockRegistry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Team module implementation.
 *
 * <p>The service only depends on ports (user, game, notification, repository, lock registry),
 * so it can be tested and run without Spring, MySQL or the other modules.</p>
 */
public final class TeamServiceImpl implements TeamService {

    private static final int MIN_CAPACITY = 2;
    private static final int MAX_CAPACITY = 10;
    private static final int MIN_DURATION = 30;
    private static final int MAX_DURATION = 480;
    private static final int MIN_TITLE = 5;
    private static final int MAX_TITLE = 50;
    private static final Set<String> VOICE_VALUES = Set.of("REQUIRED", "OPTIONAL", "NONE");

    private final TeamRepository teamRepository;
    private final UserQueryService userQueryService;
    private final GameQueryService gameQueryService;
    private final NotificationService notificationService;
    private final TeamLockRegistry lockRegistry;
    private final Clock clock;

    public TeamServiceImpl(TeamRepository teamRepository,
                           UserQueryService userQueryService,
                           GameQueryService gameQueryService,
                           NotificationService notificationService,
                           TeamLockRegistry lockRegistry,
                           Clock clock) {
        this.teamRepository = Objects.requireNonNull(teamRepository, "teamRepository");
        this.userQueryService = Objects.requireNonNull(userQueryService, "userQueryService");
        this.gameQueryService = Objects.requireNonNull(gameQueryService, "gameQueryService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.lockRegistry = Objects.requireNonNull(lockRegistry, "lockRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TeamDetail create(CreateTeamCommand command) {
        Objects.requireNonNull(command, "command");
        requireActiveUser(command.leaderId());
        if (!gameQueryService.isEnabled(command.gameId())) {
            throw new BizException(ErrorCode.GAME_NOT_FOUND);
        }
        String title = normalizeTitle(command.title());
        validateStartTime(command.startTime());
        validateDuration(command.durationMinutes());
        validateCapacity(command.capacity());
        validateVoice(command.voice());

        Team team = new Team(
                teamRepository.nextTeamId(),
                command.gameId(),
                command.leaderId(),
                title,
                normalizeRank(command.rankMin()),
                normalizeRank(command.rankMax()),
                command.startTime(),
                command.durationMinutes(),
                command.capacity(),
                command.voice(),
                command.platform(),
                command.roomCode());
        teamRepository.save(team);
        return toDetail(team, 0, true);
    }

    @Override
    public TeamDetail get(long teamId, long viewerId) {
        Team team = requireTeam(teamId);
        expireIfNeeded(team);
        int approved = teamRepository.countApproved(teamId);
        boolean canSeeRoomCode = team.getLeaderId() == viewerId
                || isApprovedMember(teamId, viewerId);
        return toDetail(team, approved, canSeeRoomCode);
    }

    @Override
    public List<TeamBrief> listByGame(long gameId) {
        List<TeamBrief> result = new ArrayList<>();
        for (Team team : teamRepository.findAll()) {
            if (team.getGameId() != gameId) {
                continue;
            }
            expireIfNeeded(team);
            if (team.getStatus() == TeamStatus.RECRUITING || team.getStatus() == TeamStatus.FULL) {
                result.add(toBrief(team, teamRepository.countApproved(team.getTeamId())));
            }
        }
        return result;
    }

    @Override
    public ApplicationBrief apply(ApplyTeamCommand command) {
        Objects.requireNonNull(command, "command");
        requireActiveUser(command.userId());
        Team team = requireTeam(command.teamId());
        expireIfNeeded(team);

        if (team.getLeaderId() == command.userId()) {
            throw new BizException(ErrorCode.CANNOT_APPLY_OWN_TEAM);
        }
        if (team.getStatus() == TeamStatus.FULL) {
            throw new BizException(ErrorCode.TEAM_FULL);
        }
        if (!team.isRecruiting()) {
            throw new BizException(ErrorCode.TEAM_CLOSED);
        }
        int approved = teamRepository.countApproved(team.getTeamId());
        if (approved + 1 >= team.getCapacity()) {
            team.markFull();
            teamRepository.save(team);
            throw new BizException(ErrorCode.TEAM_FULL);
        }
        if (teamRepository.findActiveApplication(team.getTeamId(), command.userId()).isPresent()) {
            throw new BizException(ErrorCode.DUPLICATE_APPLICATION);
        }

        TeamApplication application = new TeamApplication(
                teamRepository.nextApplicationId(),
                team.getTeamId(),
                command.userId(),
                command.message(),
                now());
        teamRepository.saveApplication(application);
        notificationService.notify(
                team.getLeaderId(),
                "TEAM_APPLY",
                "New team application received, id " + application.getApplicationId());
        return toApplicationBrief(application);
    }

    @Override
    public void approve(ApproveCommand command) {
        Objects.requireNonNull(command, "command");
        lockRegistry.withLock(command.teamId(), () -> {
            Team team = requireTeam(command.teamId());
            expireIfNeeded(team);

            if (team.getLeaderId() != command.captainId()) {
                throw new BizException(ErrorCode.NOT_TEAM_CAPTAIN);
            }
            TeamApplication application = teamRepository
                    .findApplicationById(command.teamId(), command.applicationId())
                    .orElseThrow(() -> new BizException(ErrorCode.APPLICATION_NOT_FOUND));
            if (!application.isPending()) {
                throw new BizException(ErrorCode.APPLICATION_NOT_PENDING);
            }

            if (!command.approve()) {
                application.reject();
                teamRepository.saveApplication(application);
                notificationService.notify(
                        application.getUserId(), "TEAM_RESULT", "Your application was rejected");
                return null;
            }

            if (team.getStatus() == TeamStatus.FULL) {
                application.reject();
                teamRepository.saveApplication(application);
                notificationService.notify(
                        application.getUserId(), "TEAM_RESULT", "Team is full, application rejected");
                throw new BizException(ErrorCode.TEAM_FULL);
            }
            if (!team.isRecruiting()) {
                throw new BizException(ErrorCode.TEAM_CLOSED);
            }

            int approved = teamRepository.countApproved(team.getTeamId());
            if (approved + 1 >= team.getCapacity()) {
                application.reject();
                teamRepository.saveApplication(application);
                team.markFull();
                teamRepository.save(team);
                notificationService.notify(
                        application.getUserId(), "TEAM_RESULT", "Team is full, application rejected");
                throw new BizException(ErrorCode.TEAM_FULL);
            }

            application.approve();
            teamRepository.saveApplication(application);
            if (approved + 2 == team.getCapacity()) {
                team.markFull();
                teamRepository.save(team);
            }
            notificationService.notify(
                    application.getUserId(), "TEAM_RESULT", "Application approved, welcome to the team");
            return null;
        });
    }

    @Override
    public void close(long teamId, long captainId) {
        lockRegistry.withLock(teamId, () -> {
            Team team = requireTeam(teamId);
            expireIfNeeded(team);
            if (team.getLeaderId() != captainId) {
                throw new BizException(ErrorCode.NOT_TEAM_CAPTAIN);
            }
            if (!team.isOpen()) {
                throw new BizException(ErrorCode.TEAM_CLOSED);
            }
            team.close();
            teamRepository.save(team);
            teamRepository.findApplications(teamId).stream()
                    .filter(application -> application.getStatus() == ApplicationStatus.APPROVED)
                    .forEach(application -> notificationService.notify(
                            application.getUserId(), "TEAM_RESULT", "Team has been closed"));
            return null;
        });
    }

    @Override
    public void leaveTeam(long teamId, long userId) {
        lockRegistry.withLock(teamId, () -> {
            Team team = requireTeam(teamId);
            expireIfNeeded(team);
            if (team.getLeaderId() == userId) {
                throw new BizException(ErrorCode.CAPTAIN_CANNOT_LEAVE);
            }
            TeamApplication application = teamRepository.findApplications(teamId).stream()
                    .filter(item -> item.getUserId() == userId)
                    .filter(item -> item.getStatus() == ApplicationStatus.APPROVED)
                    .findFirst()
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_TEAM_MEMBER));
            if (!team.isOpen()) {
                throw new BizException(ErrorCode.TEAM_CLOSED);
            }
            application.leave();
            teamRepository.saveApplication(application);
            if (team.getStatus() == TeamStatus.FULL) {
                team.reopen();
                teamRepository.save(team);
            }
            notificationService.notify(
                    team.getLeaderId(), "TEAM_MEMBER_LEFT", "A member left the team");
            return null;
        });
    }

    @Override
    public void cancelApplication(long teamId, long userId) {
        lockRegistry.withLock(teamId, () -> {
            TeamApplication application = teamRepository.findActiveApplication(teamId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.APPLICATION_NOT_FOUND));
            if (!application.isPending()) {
                throw new BizException(ErrorCode.APPLICATION_NOT_PENDING);
            }
            application.cancel();
            teamRepository.saveApplication(application);
            Team team = requireTeam(teamId);
            notificationService.notify(
                    team.getLeaderId(), "TEAM_APPLY", "Applicant cancelled the application");
            return null;
        });
    }

    @Override
    public void expire(long teamId) {
        lockRegistry.withLock(teamId, () -> {
            expireIfNeeded(requireTeam(teamId));
            return null;
        });
    }

    @Override
    public List<ApplicationBrief> applications(long teamId, long captainId) {
        Team team = requireTeam(teamId);
        if (team.getLeaderId() != captainId) {
            throw new BizException(ErrorCode.NOT_TEAM_CAPTAIN);
        }
        return teamRepository.findApplications(teamId).stream()
                .map(this::toApplicationBrief)
                .toList();
    }

    private void requireActiveUser(long userId) {
        if (userQueryService.isMuted(userId)) {
            throw new BizException(ErrorCode.USER_MUTED);
        }
        if (!userQueryService.isActive(userId)) {
            throw new BizException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    private Team requireTeam(long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BizException(ErrorCode.TEAM_NOT_FOUND));
    }

    private void expireIfNeeded(Team team) {
        if (team.isExpiredAt(now())) {
            team.expire();
            teamRepository.save(team);
        }
    }

    private boolean isApprovedMember(long teamId, long userId) {
        return teamRepository.findApplications(teamId).stream()
                .anyMatch(application -> application.getUserId() == userId
                        && application.getStatus() == ApplicationStatus.APPROVED);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new BizException(ErrorCode.INVALID_TITLE);
        }
        String trimmed = title.trim();
        if (trimmed.length() < MIN_TITLE || trimmed.length() > MAX_TITLE) {
            throw new BizException(ErrorCode.INVALID_TITLE);
        }
        return trimmed;
    }

    private String normalizeRank(String rank) {
        return rank == null || rank.isBlank() ? "不限" : rank.trim();
    }

    private void validateStartTime(LocalDateTime startTime) {
        if (startTime == null || !startTime.isAfter(now())) {
            throw new BizException(ErrorCode.INVALID_START_TIME);
        }
    }

    private void validateDuration(int durationMinutes) {
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            throw new BizException(ErrorCode.INVALID_DURATION);
        }
    }

    private void validateCapacity(int capacity) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new BizException(ErrorCode.INVALID_CAPACITY);
        }
    }

    private void validateVoice(String voice) {
        if (voice == null || !VOICE_VALUES.contains(voice)) {
            throw new BizException(ErrorCode.INVALID_VOICE);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private TeamBrief toBrief(Team team, int approvedCount) {
        return new TeamBrief(
                team.getTeamId(),
                team.getGameId(),
                team.getTitle(),
                team.getLeaderId(),
                team.getCapacity(),
                approvedCount,
                team.getStatus());
    }

    private TeamDetail toDetail(Team team, int approvedCount, boolean includeRoomCode) {
        return new TeamDetail(
                team.getTeamId(),
                team.getGameId(),
                team.getTitle(),
                team.getLeaderId(),
                team.getRankMin(),
                team.getRankMax(),
                team.getStartTime(),
                team.getDurationMinutes(),
                team.getCapacity(),
                approvedCount,
                team.getVoice(),
                team.getPlatform(),
                includeRoomCode ? team.getRoomCode() : null,
                team.getStatus());
    }

    private ApplicationBrief toApplicationBrief(TeamApplication application) {
        return new ApplicationBrief(
                application.getApplicationId(),
                application.getTeamId(),
                application.getUserId(),
                application.getMessage(),
                application.getStatus());
    }
}
