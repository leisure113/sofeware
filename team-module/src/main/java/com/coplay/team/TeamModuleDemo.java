package com.coplay.team;

import com.coplay.team.api.GameQueryService;
import com.coplay.team.api.NotificationService;
import com.coplay.team.api.UserQueryService;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamDetail;
import com.coplay.team.repository.InMemoryTeamRepository;
import com.coplay.team.service.TeamServiceImpl;
import com.coplay.team.support.BizException;
import com.coplay.team.support.InMemoryTeamLockRegistry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standalone demo: publish -> apply -> approve -> full -> reject.
 * Run with: java -jar target/team-module-1.0.0.jar
 */
public final class TeamModuleDemo {

    private TeamModuleDemo() {
    }

    public static void main(String[] args) {
        DemoUsers users = new DemoUsers();
        DemoGames games = new DemoGames();
        DemoNotifications notifications = new DemoNotifications();
        InMemoryTeamRepository repository = new InMemoryTeamRepository();

        TeamServiceImpl service = new TeamServiceImpl(
                repository,
                users,
                games,
                notifications,
                new InMemoryTeamLockRegistry(),
                Clock.systemDefaultZone());

        LocalDateTime start = LocalDateTime.now().plusHours(2);

        TeamDetail team = service.create(new CreateTeamCommand(
                1L, 100L, "Weekend ranked team", "Gold", "Platinum",
                start, 120, 2, "REQUIRED", "PC/Telecom-1", "ROOM-888"));
        System.out.println("Created team: " + team.teamId() + ", status=" + team.status());

        var application = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "Jungle, online at night"));
        System.out.println("Application: " + application.applicationId()
                + ", userId=" + application.userId()
                + ", status=" + application.status());

        service.approve(new ApproveCommand(team.teamId(), application.applicationId(), 1L, true));

        TeamDetail memberView = service.get(team.teamId(), 2L);
        System.out.println("After approval: approvedCount=" + memberView.approvedCount()
                + ", status=" + memberView.status()
                + ", roomCode=" + memberView.roomCode());

        try {
            service.apply(new ApplyTeamCommand(team.teamId(), 3L, "One more player"));
        } catch (BizException exception) {
            System.out.println("Third player rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        System.out.println("Notifications:");
        notifications.messages().forEach(message -> System.out.println("  - " + message));
    }

    private static final class DemoUsers implements UserQueryService {
        @Override
        public boolean isActive(long userId) {
            return userId != 4L;
        }

        @Override
        public boolean isMuted(long userId) {
            return userId == 4L;
        }
    }

    private static final class DemoGames implements GameQueryService {
        @Override
        public boolean isEnabled(long gameId) {
            return gameId == 100L;
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
