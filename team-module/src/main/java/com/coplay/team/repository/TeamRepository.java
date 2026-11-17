package com.coplay.team.repository;

import com.coplay.team.domain.Team;
import com.coplay.team.domain.TeamApplication;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for teams and applications.
 * The production implementation would be a MyBatis repository.
 */
public interface TeamRepository {

    long nextTeamId();

    long nextApplicationId();

    Team save(Team team);

    Optional<Team> findById(long teamId);

    List<Team> findAll();

    TeamApplication saveApplication(TeamApplication application);

    Optional<TeamApplication> findApplicationById(long teamId, long applicationId);

    Optional<TeamApplication> findActiveApplication(long teamId, long userId);

    List<TeamApplication> findApplications(long teamId);

    int countApproved(long teamId);
}
