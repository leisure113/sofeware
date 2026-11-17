package com.coplay.content;

import com.coplay.content.domain.Comment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("hidePost/hideComment: existence, idempotence and a single notification")
class HideContentTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("hides a post, notifies its author once and makes it unreadable")
    void hidesPostAndNotifiesAuthorOnce() {
        PostDetail post = createPost("Post to moderate", "This post will be hidden by governance.");

        service.hidePost(post.postId(), "off-topic");
        service.hidePost(post.postId(), "off-topic again");

        assertEquals(PostStatus.HIDDEN,
                repository.findPostById(post.postId()).orElseThrow().getStatus());
        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.getPost(post.postId()));
        assertTrue(service.listPosts(BOARD_ID).isEmpty());
        assertEquals(1L, notifications.countByType("CONTENT_HIDDEN"));
        FakeNotificationService.Notification notification = notifications.notifications().get(0);
        assertEquals(AUTHOR_ID, notification.userId());
        assertEquals("Your post " + post.postId() + " was hidden, reason: off-topic",
                notification.content());
    }

    @Test
    @DisplayName("hides a comment, notifies its author once and removes it from the list")
    void hidesCommentAndNotifiesAuthorOnce() {
        PostDetail post = createPost("Post with comments", "A body with a comment to moderate.");
        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), OTHER_USER_ID, "This comment will be hidden as well."));

        service.hideComment(comment.commentId(), "rude");
        service.hideComment(comment.commentId(), "rude again");

        assertEquals(CommentStatus.HIDDEN,
                repository.findCommentById(comment.commentId()).orElseThrow().getStatus());
        assertTrue(service.listComments(post.postId()).isEmpty());
        assertEquals(1L, notifications.countByType("CONTENT_HIDDEN"));
        FakeNotificationService.Notification notification = notifications.notifications()
                .stream().filter(item -> item.type().equals("CONTENT_HIDDEN")).findFirst().orElseThrow();
        assertEquals(OTHER_USER_ID, notification.userId());
        assertEquals("Your comment " + comment.commentId() + " was hidden, reason: rude",
                notification.content());
    }

    @Test
    @DisplayName("rejects hiding an unknown post")
    void rejectsUnknownPost() {
        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.hidePost(UNKNOWN_POST_ID, "spam"));
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("rejects hiding an unknown comment")
    void rejectsUnknownComment() {
        assertBizError(ErrorCode.COMMENT_NOT_FOUND, () -> service.hideComment(UNKNOWN_COMMENT_ID, "spam"));
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("hiding an already hidden post does not notify again")
    void hidingAlreadyHiddenPostDoesNotNotifyAgain() {
        PostDetail post = createPost("Twice hidden", "A body that will be hidden twice.");
        service.hidePost(post.postId(), "first reason");

        service.hidePost(post.postId(), "second reason");

        assertEquals(1L, notifications.countByType("CONTENT_HIDDEN"));
        assertEquals("Your post " + post.postId() + " was hidden, reason: first reason",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("hiding an already hidden comment does not notify again")
    void hidingAlreadyHiddenCommentDoesNotNotifyAgain() {
        PostDetail post = createPost("Twice hidden comment", "A body with a comment hidden twice.");
        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), OTHER_USER_ID, "A comment that will be hidden twice."));
        service.hideComment(comment.commentId(), "first reason");

        service.hideComment(comment.commentId(), "second reason");

        assertEquals(1L, notifications.countByType("CONTENT_HIDDEN"));
    }

    @Test
    @DisplayName("trims the reason in the notification")
    void trimsReasonInNotification() {
        PostDetail post = createPost("Reason topic", "A body to hide with a padded reason.");

        service.hidePost(post.postId(), "  off-topic  ");

        assertEquals("Your post " + post.postId() + " was hidden, reason: off-topic",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("omits the reason suffix when the reason is null")
    void omitsNullReason() {
        PostDetail post = createPost("No reason topic", "A body to hide without a reason.");

        service.hidePost(post.postId(), null);

        assertEquals("Your post " + post.postId() + " was hidden",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("omits the reason suffix when the reason is blank")
    void omitsBlankReason() {
        PostDetail post = createPost("Blank reason topic", "A body to hide with a blank reason.");

        service.hidePost(post.postId(), "   ");

        assertEquals("Your post " + post.postId() + " was hidden",
                notifications.notifications().get(0).content());
    }

    @Test
    @DisplayName("hiding a deleted post is a no-op without a notification")
    void hidingDeletedPostIsNoOp() {
        Post post = seedPost(101L, BOARD_ID, AUTHOR_ID, "Deleted topic",
                "Body of a deleted post.", NOW.minusDays(1));
        forcePostStatus(post, PostStatus.DELETED);

        service.hidePost(post.getPostId(), "spam");

        assertEquals(PostStatus.DELETED, post.getStatus());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("hiding a deleted comment is a no-op without a notification")
    void hidingDeletedCommentIsNoOp() {
        PostDetail post = createPost("Deleted comment", "A body with a deleted comment.");
        Comment comment = seedComment(501L, post.postId(), OTHER_USER_ID,
                "A deleted comment", NOW);
        forceCommentStatus(comment, CommentStatus.DELETED);

        service.hideComment(comment.getCommentId(), "spam");

        assertEquals(CommentStatus.DELETED, comment.getStatus());
        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("governance can still hide a comment after its post was hidden")
    void hidesCommentAfterItsPostWasHidden() {
        PostDetail post = createPost("Hidden post", "A body with a comment on a hidden post.");
        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), OTHER_USER_ID, "A comment that will be hidden later."));
        service.hidePost(post.postId(), "off-topic");

        service.hideComment(comment.commentId(), "rude");

        assertEquals(CommentStatus.HIDDEN,
                repository.findCommentById(comment.commentId()).orElseThrow().getStatus());
        assertEquals(2L, notifications.countByType("CONTENT_HIDDEN"));
    }
}
