package com.coplay.governance.fake;

import com.coplay.governance.api.UserAdminService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeUserAdminService implements UserAdminService {

    private final List<Action> actions = new CopyOnWriteArrayList<>();

    @Override
    public void muteUser(long userId, String reason) {
        actions.add(new Action("MUTE_USER", userId, reason));
    }

    @Override
    public void banUser(long userId, String reason) {
        actions.add(new Action("BAN_USER", userId, reason));
    }

    public List<Action> actions() {
        return List.copyOf(actions);
    }

    public long countByType(String type) {
        return actions.stream().filter(action -> action.type().equals(type)).count();
    }

    public List<Long> mutedUserIds() {
        return actions.stream()
                .filter(action -> action.type().equals("MUTE_USER"))
                .map(Action::userId)
                .toList();
    }

    public List<Long> bannedUserIds() {
        return actions.stream()
                .filter(action -> action.type().equals("BAN_USER"))
                .map(Action::userId)
                .toList();
    }

    public record Action(String type, long userId, String reason) {
    }
}
