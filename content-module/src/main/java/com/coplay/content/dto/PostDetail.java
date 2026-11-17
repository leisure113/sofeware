package com.coplay.content.dto;

import com.coplay.content.domain.PostStatus;

import java.time.LocalDateTime;

/**
 * Full post view shown on the post detail page.
 */
public record PostDetail(
        long postId,
        long boardId,
        long authorId,
        String title,
        String content,
        PostStatus status,
        LocalDateTime createdAt) {
}
