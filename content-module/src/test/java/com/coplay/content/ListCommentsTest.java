package com.coplay.content;

import com.coplay.content.domain.Comment;
import com.coplay.content.domain.CommentStatus;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("listComments: post existence, visibility and ordering")
class ListCommentsTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("rejects an unknown post")
    void rejectsUnknownPost() {
        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.listComments(UNKNOWN_POST_ID));
    }

    @Test
    @DisplayName("returns an empty list when the post has no comments")
    void returnsEmptyListWhenNoComments() {
        PostDetail post = createPost("Quiet topic", "Nobody has commented yet.");

        assertTrue(service.listComments(post.postId()).isEmpty());
    }

    @Test
    @DisplayName("returns only PUBLISHED comments and skips hidden and deleted ones")
    void returnsOnlyPublishedComments() {
        PostDetail post = createPost("Moderated topic", "A body with moderated comments.");
        Comment published = seedComment(501L, post.postId(), OTHER_USER_ID,
                "Published comment", NOW.minusMinutes(30));
        Comment hidden = seedComment(502L, post.postId(), OTHER_USER_ID,
                "Hidden comment", NOW.minusMinutes(20));
        Comment deleted = seedComment(503L, post.postId(), OTHER_USER_ID,
                "Deleted comment", NOW.minusMinutes(10));
        service.hideComment(hidden.getCommentId(), "rude");
        forceCommentStatus(deleted, CommentStatus.DELETED);

        List<CommentBrief> comments = service.listComments(post.postId());

        assertEquals(List.of(published.getCommentId()), ids(comments));
        assertEquals(CommentStatus.PUBLISHED, comments.get(0).status());
    }

    @Test
    @DisplayName("orders comments by createdAt descending")
    void ordersByCreatedAtDescending() {
        PostDetail post = createPost("Ordered topic", "A body with ordered comments.");
        seedComment(601L, post.postId(), OTHER_USER_ID, "Oldest", NOW.minusMinutes(30));
        seedComment(602L, post.postId(), OTHER_USER_ID, "Newest", NOW.minusMinutes(10));
        seedComment(603L, post.postId(), OTHER_USER_ID, "Middle", NOW.minusMinutes(20));

        assertEquals(List.of(602L, 603L, 601L), ids(service.listComments(post.postId())));
    }

    @Test
    @DisplayName("orders comments with the same createdAt by comment id descending")
    void ordersByIdDescendingWhenCreatedAtTies() {
        PostDetail post = createPost("Tied comments", "A body with tied comments.");
        LocalDateTime sharedTime = NOW.minusMinutes(5);
        seedComment(701L, post.postId(), OTHER_USER_ID, "First tied", sharedTime);
        seedComment(703L, post.postId(), OTHER_USER_ID, "Third tied", sharedTime);
        seedComment(702L, post.postId(), OTHER_USER_ID, "Second tied", sharedTime);

        assertEquals(List.of(703L, 702L, 701L), ids(service.listComments(post.postId())));
    }

    @Test
    @DisplayName("excludes comments that belong to another post")
    void excludesCommentsOfOtherPosts() {
        PostDetail post = createPost("First topic", "A body for the first topic.");
        PostDetail otherPost = createPost("Second topic", "A body for the second topic.");
        seedComment(801L, otherPost.postId(), OTHER_USER_ID, "Comment on the other post", NOW);

        assertTrue(service.listComments(post.postId()).isEmpty());
        assertEquals(List.of(801L), ids(service.listComments(otherPost.postId())));
    }

    @Test
    @DisplayName("returns brief views with id, post, author, content, status and createdAt")
    void returnsBriefViews() {
        PostDetail post = createPost("Brief comments", "A body with a brief comment.");
        CommentBrief created = service.addComment(
                new CreateCommentCommand(post.postId(), OTHER_USER_ID, "A brief comment"));

        CommentBrief comment = service.listComments(post.postId()).get(0);

        assertEquals(created.commentId(), comment.commentId());
        assertEquals(post.postId(), comment.postId());
        assertEquals(OTHER_USER_ID, comment.authorId());
        assertEquals("A brief comment", comment.content());
        assertEquals(CommentStatus.PUBLISHED, comment.status());
        assertEquals(NOW, comment.createdAt());
    }

    private static List<Long> ids(List<CommentBrief> comments) {
        return comments.stream().map(CommentBrief::commentId).toList();
    }
}
