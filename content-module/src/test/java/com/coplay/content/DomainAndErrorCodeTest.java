package com.coplay.content;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.CommentStatus;
import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.support.BizException;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Domain objects, status enums and error codes")
class DomainAndErrorCodeTest {

    private static final LocalDateTime CREATED_AT =
            ContentServiceTestSupport.NOW.minusDays(1);

    @Test
    @DisplayName("BizException exposes the stable error code and message")
    void bizExceptionExposesCodeAndMessage() {
        BizException exception = new BizException(ErrorCode.INVALID_TITLE);

        assertEquals("CONTENT-422", exception.getCode());
        assertEquals(ErrorCode.INVALID_TITLE.getMessage(), exception.getMessage());
    }

    @Test
    @DisplayName("BizException supports an extra detail in the message")
    void bizExceptionSupportsDetail() {
        BizException exception = new BizException(ErrorCode.INVALID_CONTENT, "length 0");

        assertEquals(ErrorCode.INVALID_CONTENT.getCode(), exception.getCode());
        assertEquals(ErrorCode.INVALID_CONTENT.getMessage() + ": length 0", exception.getMessage());
    }

    @Test
    @DisplayName("every error code has a non-blank stable code and message")
    void errorCodesAreStable() {
        assertEquals(11, ErrorCode.values().length);
        assertSame(ErrorCode.POST_NOT_FOUND, ErrorCode.valueOf("POST_NOT_FOUND"));
        assertSame(ErrorCode.NOT_POST_AUTHOR, ErrorCode.valueOf("NOT_POST_AUTHOR"));
        assertEquals("CONTENT-404", ErrorCode.POST_NOT_FOUND.getCode());
        assertEquals("CONTENT-403", ErrorCode.NOT_POST_AUTHOR.getCode());

        for (ErrorCode errorCode : ErrorCode.values()) {
            assertNotNull(errorCode.getCode(), errorCode.name());
            assertFalse(errorCode.getCode().isBlank(), errorCode.name());
            assertNotNull(errorCode.getMessage(), errorCode.name());
            assertFalse(errorCode.getMessage().isBlank(), errorCode.name());
        }
    }

    @Test
    @DisplayName("Board exposes its fields and disabling is idempotent")
    void boardDisableIsIdempotent() {
        Board board = new Board(1L, 100L, "General discussion");

        assertEquals(1L, board.getBoardId());
        assertEquals(100L, board.getGameId());
        assertEquals("General discussion", board.getName());
        assertTrue(board.isEnabled());

        board.disable();
        board.disable();

        assertFalse(board.isEnabled());
        assertFalse(new Board(2L, 100L, "Archive", false).isEnabled());
    }

    @Test
    @DisplayName("a new post is PUBLISHED and hiding is idempotent")
    void postStartsPublishedAndHideIsIdempotent() {
        Post post = new Post(101L, 1L, 2L, "Title", "Content", CREATED_AT);

        assertEquals(101L, post.getPostId());
        assertEquals(1L, post.getBoardId());
        assertEquals(2L, post.getAuthorId());
        assertEquals("Title", post.getTitle());
        assertEquals("Content", post.getContent());
        assertEquals(CREATED_AT, post.getCreatedAt());
        assertEquals(PostStatus.PUBLISHED, post.getStatus());
        assertTrue(post.isPublished());

        post.hide();
        post.hide();

        assertEquals(PostStatus.HIDDEN, post.getStatus());
        assertFalse(post.isPublished());
    }

    @Test
    @DisplayName("Post.edit replaces the text and keeps identity, createdAt and status")
    void postEditReplacesText() {
        Post post = new Post(101L, 1L, 2L, "Title", "Content", CREATED_AT);

        post.edit("New title", "New content");

        assertEquals("New title", post.getTitle());
        assertEquals("New content", post.getContent());
        assertEquals(101L, post.getPostId());
        assertEquals(1L, post.getBoardId());
        assertEquals(2L, post.getAuthorId());
        assertEquals(CREATED_AT, post.getCreatedAt());
        assertEquals(PostStatus.PUBLISHED, post.getStatus());
        assertThrows(NullPointerException.class, () -> post.edit(null, "New content"));
        assertThrows(NullPointerException.class, () -> post.edit("New title", null));
    }

    @Test
    @DisplayName("a new comment is PUBLISHED and hiding is idempotent")
    void commentStartsPublishedAndHideIsIdempotent() {
        Comment comment = new Comment(501L, 101L, 3L, "Content", CREATED_AT);

        assertEquals(501L, comment.getCommentId());
        assertEquals(101L, comment.getPostId());
        assertEquals(3L, comment.getAuthorId());
        assertEquals("Content", comment.getContent());
        assertEquals(CREATED_AT, comment.getCreatedAt());
        assertEquals(CommentStatus.PUBLISHED, comment.getStatus());
        assertTrue(comment.isPublished());

        comment.hide();
        comment.hide();

        assertEquals(CommentStatus.HIDDEN, comment.getStatus());
        assertFalse(comment.isPublished());
    }

    @Test
    @DisplayName("post and comment status enums expose the documented states")
    void statusEnumsExposeDocumentedStates() {
        assertEquals(3, PostStatus.values().length);
        assertSame(PostStatus.HIDDEN, PostStatus.valueOf("HIDDEN"));
        assertSame(PostStatus.DELETED, PostStatus.valueOf("DELETED"));
        assertEquals(3, CommentStatus.values().length);
        assertSame(CommentStatus.HIDDEN, CommentStatus.valueOf("HIDDEN"));
        assertSame(CommentStatus.DELETED, CommentStatus.valueOf("DELETED"));
    }

    @Test
    @DisplayName("domain constructors reject null required text")
    void domainConstructorsRejectNullText() {
        assertThrows(NullPointerException.class,
                () -> new Board(1L, 100L, null));
        assertThrows(NullPointerException.class,
                () -> new Post(101L, 1L, 2L, null, "Content", CREATED_AT));
        assertThrows(NullPointerException.class,
                () -> new Post(101L, 1L, 2L, "Title", null, CREATED_AT));
        assertThrows(NullPointerException.class,
                () -> new Post(101L, 1L, 2L, "Title", "Content", null));
        assertThrows(NullPointerException.class,
                () -> new Comment(501L, 101L, 3L, null, CREATED_AT));
        assertThrows(NullPointerException.class,
                () -> new Comment(501L, 101L, 3L, "Content", null));
    }
}
