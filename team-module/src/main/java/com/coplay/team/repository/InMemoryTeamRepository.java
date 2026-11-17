package com.coplay.team.repository;

import com.coplay.team.domain.ApplicationStatus;
import com.coplay.team.domain.Team;
import com.coplay.team.domain.TeamApplication;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository used by the standalone demo and tests.
 * All mutating operations are synchronized so the module is safe without a database.
 */
public final class InMemoryTeamRepository implements TeamRepository {

    private final Map<Long, Team> teams = new ConcurrentHashMap<>();
    private final Map<Long, List<TeamApplication>> applications = new ConcurrentHashMap<>();
    private final AtomicLong teamIds = new AtomicLong(1000);
    private final AtomicLong applicationIds = new AtomicLong(5000);

    @Override
    public long nextTeamId() {
        return teamIds.incrementAndGet();
    }

    @Override
    public long nextApplicationId() {
        return applicationIds.incrementAndGet();
    }

    @Override
    public synchronized Team save(Team team) {
        teams.put(team.getTeamId(), team);
        return team;
    }

    @Override
    public Optional<Team> findById(long teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public List<Team> findAll() {
        return List.copyOf(teams.values());
    }

    @Override
    public synchronized TeamApplication saveApplication(TeamApplication application) {
        List<TeamApplication> teamApplications =
                applications.computeIfAbsent(application.getTeamId(), ignored -> new CopyOnWriteArrayList<>());
        for (int i = 0; i < teamApplications.size(); i++) {
            if (teamApplications.get(i).getApplicationId() == application.getApplicationId()) {
                teamApplications.set(i, application);
                return application;
            }
        }
        teamApplications.add(application);
        return application;
    }

    @Override
    public Optional<TeamApplication> findApplicationById(long teamId, long applicationId) {
        return applications.getOrDefault(teamId, List.of()).stream()
                .filter(application -> application.getApplicationId() == applicationId)
                .findFirst();
    }

    @Override
    public Optional<TeamApplication> findActiveApplication(long teamId, long userId) {
        return applications.getOrDefault(teamId, List.of()).stream()
                .filter(application -> application.getUserId() == userId)
                .filter(TeamApplication::isActive)
                .findFirst();
    }

    @Override
    public List<TeamApplication> findApplications(long teamId) {
        return List.copyOf(applications.getOrDefault(teamId, List.of()));
    }

    @Override
    public int countApproved(long teamId) {
        return (int) applications.getOrDefault(teamId, List.of()).stream()
                .filter(application -> application.getStatus() == ApplicationStatus.APPROVED)
                .count();
    }
}
