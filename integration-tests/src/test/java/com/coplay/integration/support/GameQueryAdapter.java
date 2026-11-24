package com.coplay.integration.support;

import java.util.Set;

/**
 * Simple game catalogue adapter used by the content and team modules.
 */
public final class GameQueryAdapter implements
        com.coplay.content.api.GameQueryService,
        com.coplay.team.api.GameQueryService {

    private final Set<Long> enabledGames;

    public GameQueryAdapter(Set<Long> enabledGames) {
        this.enabledGames = Set.copyOf(enabledGames);
    }

    @Override
    public boolean isGameEnabled(long gameId) {
        return enabledGames.contains(gameId);
    }

    @Override
    public boolean isEnabled(long gameId) {
        return enabledGames.contains(gameId);
    }
}
