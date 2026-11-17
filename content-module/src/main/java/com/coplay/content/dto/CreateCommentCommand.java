package com.coplay.content.dto;

/**
 * Command for adding a comment to a post.
 */
public record CreateCommentCommand(
        long postId,
        long authorId,
        String content) {
}
