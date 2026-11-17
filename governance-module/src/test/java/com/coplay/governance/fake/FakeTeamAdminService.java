package com.coplay.governance.fake;

import com.coplay.governance.api.TeamAdminService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeTeamAdminService implements TeamAdminService {

    private final List<Action> actions = new CopyOnWriteArrayList<>();

    @Override
    public void closeTeam(long teamId, String reason) {
        actions.add(new Action("CLOSE_TEAM", teamId, reason));
    }

    public List<Action> actions() {
        return List.copyOf(actions);
    }

    public List<Long> closedTeamIds() {
        return actions.stream().map(Action::teamId).toList();
    }

    public record Action(String type, long teamId, String reason) {
    }
}
