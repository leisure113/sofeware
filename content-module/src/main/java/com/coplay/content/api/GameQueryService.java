package com.coplay.content.api;

/**
 * Read-only game data required by the content module.
 * In the full project this is implemented by an adapter over the game catalogue.
 */
public interface GameQueryService {

    boolean isGameEnabled(long gameId);
}
