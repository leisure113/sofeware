package com.coplay.governance.fake;

import com.coplay.governance.api.ContentAdminService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeContentAdminService implements ContentAdminService {

    private final List<Action> actions = new CopyOnWriteArrayList<>();
    private String failureMessage;

    public FakeContentAdminService failWith(String message) {
        this.failureMessage = message;
        return this;
    }

    @Override
    public void hidePost(long postId, String reason) {
        record(new Action("HIDE_POST", postId, reason));
    }

    @Override
    public void hideComment(long commentId, String reason) {
        record(new Action("HIDE_COMMENT", commentId, reason));
    }

    public List<Action> actions() {
        return List.copyOf(actions);
    }

    public long countByType(String type) {
        return actions.stream().filter(action -> action.type().equals(type)).count();
    }

    private void record(Action action) {
        if (failureMessage != null) {
            throw new IllegalStateException(failureMessage);
        }
        actions.add(action);
    }

    public record Action(String type, long targetId, String reason) {
    }
}
