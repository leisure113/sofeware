package com.coplay.governance.api;

/**
 * Port to the team module: moderation actions on teams.
 */
public interface TeamAdminService {

    void closeTeam(long teamId, String reason);
}
