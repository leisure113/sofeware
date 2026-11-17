package com.coplay.content.dto;

import com.coplay.content.domain.PostStatus;

import java.time.LocalDateTime;

/**
 * Lightweight post view for list pages.
 */
public record PostBrief(
        long postId,
        long boardId,
        long authorId,
        String title,
        PostStatus status,
        LocalDateTime createdAt) {
}
