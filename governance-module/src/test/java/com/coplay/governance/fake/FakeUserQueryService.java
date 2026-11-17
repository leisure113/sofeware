package com.coplay.governance.fake;

import com.coplay.governance.api.UserQueryService;

import java.util.HashSet;
import java.util.Set;

public final class FakeUserQueryService implements UserQueryService {

    private final Set<Long> active = new HashSet<>(Set.of(1L, 2L, 3L, 4L));

    public FakeUserQueryService activate(long userId) {
        active.add(userId);
        return this;
    }

    public FakeUserQueryService deactivate(long userId) {
        active.remove(userId);
        return this;
    }

    @Override
    public boolean isActive(long userId) {
        return active.contains(userId);
    }
}
