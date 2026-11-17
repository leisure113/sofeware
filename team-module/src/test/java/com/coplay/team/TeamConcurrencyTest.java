package com.coplay.team;

import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.TeamStatus;
import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamDetail;
import com.coplay.team.fake.FakeGameQueryService;
import com.coplay.team.fake.FakeNotificationService;
import com.coplay.team.fake.FakeUserQueryService;
import com.coplay.team.repository.InMemoryTeamRepository;
import com.coplay.team.service.TeamServiceImpl;
import com.coplay.team.support.BizException;
import com.coplay.team.support.InMemoryTeamLockRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamConcurrencyTest {

    @Test
    @DisplayName("two concurrent approvals: only one gets the last slot")
    void onlyOneApplicantGetsLastSlot() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now(clock);
        InMemoryTeamRepository repository = new InMemoryTeamRepository();
        TeamServiceImpl service = new TeamServiceImpl(
                repository,
                new FakeUserQueryService(),
                new FakeGameQueryService(),
                new FakeNotificationService(),
                new InMemoryTeamLockRegistry(),
                clock);

        TeamDetail team = service.create(new CreateTeamCommand(
                1L, 100L, "Last slot test", "Gold", "Platinum",
                now.plusHours(2), 120, 2, "REQUIRED", "PC", "ROOM-1"));
        var first = service.apply(new ApplyTeamCommand(team.teamId(), 2L, "first"));
        var second = service.apply(new ApplyTeamCommand(team.teamId(), 3L, "second"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> firstResult = pool.submit(() -> approve(service, team.teamId(),
                    first.applicationId(), ready, start));
            Future<Boolean> secondResult = pool.submit(() -> approve(service, team.teamId(),
                    second.applicationId(), ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0);
            assertEquals(1, successes, "exactly one approval should succeed");

            TeamDetail detail = service.get(team.teamId(), 1L);
            assertEquals(TeamStatus.FULL, detail.status());
            assertEquals(1, detail.approvedCount());

            List<ApplicationStatus> statuses = repository.findApplications(team.teamId()).stream()
                    .map(application -> application.getStatus())
                    .toList();
            assertEquals(1, statuses.stream().filter(status -> status == ApplicationStatus.APPROVED).count());
            assertEquals(1, statuses.stream().filter(status -> status == ApplicationStatus.REJECTED).count());
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean approve(TeamServiceImpl service,
                            long teamId,
                            long applicationId,
                            CountDownLatch ready,
                            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            service.approve(new ApproveCommand(teamId, applicationId, 1L, true));
            return true;
        } catch (BizException exception) {
            return false;
        }
    }
}
