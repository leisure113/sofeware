package com.coplay.content.fake;

import com.coplay.content.api.GameQueryService;

import java.util.HashSet;
import java.util.Set;

public final class FakeGameQueryService implements GameQueryService {

    private final Set<Long> enabled = new HashSet<>(Set.of(100L));

    public FakeGameQueryService enable(long gameId) {
        enabled.add(gameId);
        return this;
    }

    public FakeGameQueryService disable(long gameId) {
        enabled.remove(gameId);
        return this;
    }

    @Override
    public boolean isGameEnabled(long gameId) {
        return enabled.contains(gameId);
    }
}
