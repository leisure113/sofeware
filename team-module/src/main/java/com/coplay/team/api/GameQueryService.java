package com.coplay.team.api;

/**
 * Read-only game data required by the team module.
 * In the full project this is implemented by an adapter over the content module.
 */
public interface GameQueryService {

    boolean isEnabled(long gameId);
}
