package com.coplay.content.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Post aggregate root. A post is created as PUBLISHED and can only move to HIDDEN
 * through the content module; DELETED is reserved for the governance adapter.
 */
public final class Post {

    private final long postId;
    private final long boardId;
    private final long authorId;
    private final String title;
    private final String content;
    private final LocalDateTime createdAt;
    private PostStatus status;

    public Post(long postId,
                long boardId,
                long authorId,
                String title,
                String content,
                LocalDateTime createdAt) {
        this.postId = postId;
        this.boardId = boardId;
        this.authorId = authorId;
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = PostStatus.PUBLISHED;
    }

    public long getPostId() {
        return postId;
    }

    public long getBoardId() {
        return boardId;
    }

    public long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public PostStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    /**
     * Hides a published post. Hiding an already hidden or deleted post is a no-op,
     * which keeps the moderation use case idempotent.
     */
    public void hide() {
        if (status == PostStatus.PUBLISHED) {
            status = PostStatus.HIDDEN;
        }
    }
}
