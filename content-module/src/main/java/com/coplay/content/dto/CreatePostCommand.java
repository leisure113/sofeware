package com.coplay.content.dto;

/**
 * Command for publishing a post. The game is derived from the board.
 */
public record CreatePostCommand(
        long boardId,
        long authorId,
        String title,
        String content) {
}
