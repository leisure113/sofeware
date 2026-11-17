package com.coplay.team.fake;

import com.coplay.team.api.GameQueryService;

import java.util.HashSet;
import java.util.Set;

public final class FakeGameQueryService implements GameQueryService {

    private final Set<Long> enabled = new HashSet<>(Set.of(100L));

    public FakeGameQueryService enable(long gameId) {
        enabled.add(gameId);
        return this;
    }

    @Override
    public boolean isEnabled(long gameId) {
        return enabled.contains(gameId);
    }
}
