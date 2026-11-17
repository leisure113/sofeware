package com.coplay.content.domain;

import java.util.Objects;

/**
 * Discussion board aggregate root. A board belongs to exactly one game.
 * Governance can disable a board without deleting it.
 */
public final class Board {

    private final long boardId;
    private final long gameId;
    private final String name;
    private boolean enabled;

    public Board(long boardId, long gameId, String name) {
        this(boardId, gameId, name, true);
    }

    public Board(long boardId, long gameId, String name, boolean enabled) {
        this.boardId = boardId;
        this.gameId = gameId;
        this.name = Objects.requireNonNull(name, "name");
        this.enabled = enabled;
    }

    public long getBoardId() {
        return boardId;
    }

    public long getGameId() {
        return gameId;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Disables the board. Calling it again is a no-op, so governance code can be idempotent.
     */
    public void disable() {
        enabled = false;
    }
}
