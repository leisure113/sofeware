package com.coplay.content.dto;

import com.coplay.content.domain.CommentStatus;

import java.time.LocalDateTime;

/**
 * Comment view returned by the comment list and by addComment.
 */
public record CommentBrief(
        long commentId,
        long postId,
        long authorId,
        String content,
        CommentStatus status,
        LocalDateTime createdAt) {
}
