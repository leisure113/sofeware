package com.coplay.content;

import com.coplay.content.domain.CommentStatus;
import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.fake.FakeNotificationService;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("addComment: validation, visibility and author notification")
class AddCommentTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("adds a PUBLISHED comment, persists it and notifies the post author once")
    void addsPublishedCommentAndNotifiesPostAuthor() {
        PostDetail post = createPost("Jungle guide", "A short guide for the new season.");

        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), OTHER_USER_ID, "Great write-up, thanks for sharing!"));

        assertEquals(post.postId(), comment.postId());
        assertEquals(OTHER_USER_ID, comment.authorId());
        assertEquals("Great write-up, thanks for sharing!", comment.content());
        assertEquals(CommentStatus.PUBLISHED, comment.status());
        assertEquals(NOW, comment.createdAt());
        assertEquals(post.postId(), repository.findCommentById(comment.commentId()).orElseThrow().getPostId());
        assertEquals(List.of(comment.commentId()),
                service.listComments(post.postId()).stream().map(CommentBrief::commentId).toList());

        assertEquals(1L, notifications.countByType("POST_COMMENT"));
        FakeNotificationService.Notification notification = notifications.notifications().get(0);
        assertEquals(AUTHOR_ID, notification.userId());
        assertEquals("POST_COMMENT", notification.type());
        assertEquals("New comment on your post " + post.postId()
                + ", comment id " + comment.commentId(), notification.content());
    }

    @Test
    @DisplayName("trims the comment content before saving it")
    void trimsCommentContent() {
        PostDetail post = createPost("Trimming topic", "A body to comment on.");

        CommentBrief comment = service.addComment(
                new CreateCommentCommand(post.postId(), OTHER_USER_ID, "   Nice post   "));

        assertEquals("Nice post", comment.content());
        assertEquals("Nice post",
                repository.findCommentById(comment.commentId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("assigns strictly increasing comment ids")
    void assignsIncreasingCommentIds() {
        PostDetail post = createPost("Comment ids", "A body to comment on.");

        CommentBrief first = service.addComment(
                new CreateCommentCommand(post.postId(), OTHER_USER_ID, "First comment"));
        CommentBrief second = service.addComment(
                new CreateCommentCommand(post.postId(), OTHER_USER_ID, "Second comment"));

        assertTrue(second.commentId() > first.commentId());
    }

    @ParameterizedTest(name = "comment with {0} characters is accepted")
    @ValueSource(ints = {1, 500})
    void acceptsCommentAtBoundaries(int length) {
        PostDetail post = createPost("Boundary comments", "A body to comment on.");
        String content = "c".repeat(length);

        CommentBrief comment = service.addComment(
                new CreateCommentCommand(post.postId(), OTHER_USER_ID, content));

        assertEquals(content, comment.content());
        assertEquals(length, comment.content().length());
    }

    @ParameterizedTest(name = "comment with {0} characters is rejected")
    @ValueSource(ints = {0, 501, 800})
    void rejectsCommentOutsideBoundaries(int length) {
        PostDetail post = createPost("Boundary comments", "A body to comment on.");
        String content = "c".repeat(length);

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, content)));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("rejects a null comment")
    void rejectsNullComment() {
        PostDetail post = createPost("Null comment", "A body to comment on.");

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, null)));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
    }

    @ParameterizedTest(name = "whitespace-only comment \"{0}\" is rejected")
    @ValueSource(strings = {" ", "   ", "\n\t "})
    void rejectsBlankComment(String content) {
        PostDetail post = createPost("Blank comment", "A body to comment on.");

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, content)));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("rejects a comment on an unknown post")
    void rejectsUnknownPost() {
        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.addComment(new CreateCommentCommand(UNKNOWN_POST_ID, OTHER_USER_ID, "Hello")));
        assertEquals(0, notifications.notifications().size());
    }

    @Test
    @DisplayName("rejects a comment on a hidden post")
    void rejectsHiddenPost() {
        PostDetail post = createPost("Hidden discussion", "The body of a post that will be hidden.");
        service.hidePost(post.postId(), "off-topic");

        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, "Too late")));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("rejects a comment on a deleted post")
    void rejectsDeletedPost() {
        Post post = seedPost(101L, BOARD_ID, AUTHOR_ID, "Deleted topic",
                "Body of a deleted post.", NOW.minusDays(1));
        forcePostStatus(post, PostStatus.DELETED);

        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.addComment(new CreateCommentCommand(post.getPostId(), OTHER_USER_ID, "Hello")));
        assertTrue(repository.findCommentsByPost(post.getPostId()).isEmpty());
    }

    @Test
    @DisplayName("rejects an inactive commenter")
    void rejectsInactiveUser() {
        PostDetail post = createPost("Inactive commenter", "A body to comment on.");
        users.deactivate(OTHER_USER_ID);

        assertBizError(ErrorCode.USER_NOT_ACTIVE,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, "Hello")));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("rejects an unknown commenter as inactive")
    void rejectsUnknownUser() {
        PostDetail post = createPost("Unknown commenter", "A body to comment on.");

        assertBizError(ErrorCode.USER_NOT_ACTIVE,
                () -> service.addComment(new CreateCommentCommand(post.postId(), UNKNOWN_USER_ID, "Hello")));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
    }

    @Test
    @DisplayName("rejects a muted commenter")
    void rejectsMutedUser() {
        PostDetail post = createPost("Muted commenter", "A body to comment on.");
        users.mute(OTHER_USER_ID);

        assertBizError(ErrorCode.USER_MUTED,
                () -> service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, "Hello")));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("rejects a sensitive comment and does not notify")
    void rejectsSensitiveComment() {
        PostDetail post = createPost("Sensitive comments", "A body to comment on.");

        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> service.addComment(new CreateCommentCommand(
                        post.postId(), OTHER_USER_ID, "This contains FORBIDDEN words.")));
        assertTrue(repository.findCommentsByPost(post.postId()).isEmpty());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("notifies the post author even when someone else comments")
    void notifiesPostAuthorNotCommenter() {
        PostDetail post = createPost(BOARD_ID, AUTHOR_ID, "Author topic", "A body to comment on.");

        service.addComment(new CreateCommentCommand(post.postId(), OTHER_USER_ID, "A friendly comment"));

        FakeNotificationService.Notification notification = notifications.notifications().get(0);
        assertEquals(AUTHOR_ID, notification.userId());
        assertTrue(notification.userId() != OTHER_USER_ID);
    }

    @Test
    @DisplayName("rejects a null command with a NullPointerException")
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> service.addComment(null));
    }
}
