package com.coplay.content.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Comment aggregate root. A comment is created as PUBLISHED and can only move to HIDDEN
 * through the content module; DELETED is reserved for the governance adapter.
 */
public final class Comment {

    private final long commentId;
    private final long postId;
    private final long authorId;
    private final String content;
    private final LocalDateTime createdAt;
    private CommentStatus status;

    public Comment(long commentId,
                   long postId,
                   long authorId,
                   String content,
                   LocalDateTime createdAt) {
        this.commentId = commentId;
        this.postId = postId;
        this.authorId = authorId;
        this.content = Objects.requireNonNull(content, "content");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = CommentStatus.PUBLISHED;
    }

    public long getCommentId() {
        return commentId;
    }

    public long getPostId() {
        return postId;
    }

    public long getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isPublished() {
        return status == CommentStatus.PUBLISHED;
    }

    /**
     * Hides a published comment. Hiding an already hidden or deleted comment is a no-op,
     * which keeps the moderation use case idempotent.
     */
    public void hide() {
        if (status == CommentStatus.PUBLISHED) {
            status = CommentStatus.HIDDEN;
        }
    }
}
