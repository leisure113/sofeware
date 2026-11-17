package com.coplay.content.fake;

import com.coplay.content.api.UserQueryService;

import java.util.HashSet;
import java.util.Set;

public final class FakeUserQueryService implements UserQueryService {

    private final Set<Long> active = new HashSet<>(Set.of(1L, 2L, 3L));
    private final Set<Long> muted = new HashSet<>();

    public FakeUserQueryService activate(long userId) {
        active.add(userId);
        return this;
    }

    public FakeUserQueryService deactivate(long userId) {
        active.remove(userId);
        return this;
    }

    public FakeUserQueryService mute(long userId) {
        muted.add(userId);
        return this;
    }

    @Override
    public boolean isActive(long userId) {
        return active.contains(userId);
    }

    @Override
    public boolean isMuted(long userId) {
        return muted.contains(userId);
    }
}
