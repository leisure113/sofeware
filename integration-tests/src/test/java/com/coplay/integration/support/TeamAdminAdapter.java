package com.coplay.integration.support;

import com.coplay.team.api.TeamService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter from team module to governance moderation port.
 * TeamService.close needs the captain id, so the integration fixture registers
 * the captain for every team it creates.
 */
public final class TeamAdminAdapter implements com.coplay.governance.api.TeamAdminService {

    private final TeamService teamService;
    private final Map<Long, Long> captainsByTeam = new ConcurrentHashMap<>();

    public TeamAdminAdapter(TeamService teamService) {
        this.teamService = teamService;
    }

    public void registerCaptain(long teamId, long captainId) {
        captainsByTeam.put(teamId, captainId);
    }

    @Override
    public void closeTeam(long teamId, String reason) {
        Long captainId = captainsByTeam.get(teamId);
        if (captainId == null) {
            throw new IllegalStateException("No captain registered for team " + teamId);
        }
        teamService.close(teamId, captainId);
    }
}
