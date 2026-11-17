package com.coplay.team.api;

import com.coplay.team.dto.ApproveCommand;
import com.coplay.team.dto.ApplyTeamCommand;
import com.coplay.team.dto.ApplicationBrief;
import com.coplay.team.dto.CreateTeamCommand;
import com.coplay.team.dto.TeamBrief;
import com.coplay.team.dto.TeamDetail;

import java.util.List;

/**
 * Public use cases of the team module.
 */
public interface TeamService {

    TeamDetail create(CreateTeamCommand command);

    TeamDetail get(long teamId, long viewerId);

    List<TeamBrief> listByGame(long gameId);

    ApplicationBrief apply(ApplyTeamCommand command);

    void approve(ApproveCommand command);

    void close(long teamId, long captainId);

    void leaveTeam(long teamId, long userId);

    void cancelApplication(long teamId, long userId);

    void expire(long teamId);

    List<ApplicationBrief> applications(long teamId, long captainId);
}
