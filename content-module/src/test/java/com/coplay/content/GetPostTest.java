package com.coplay.content;

import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("getPost: visibility of published, hidden and deleted posts")
class GetPostTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("returns the full detail of a published post")
    void returnsPublishedPost() {
        PostDetail created = createPost("Readable topic", "This body is visible to everyone.");

        PostDetail post = service.getPost(created.postId());

        assertEquals(created.postId(), post.postId());
        assertEquals(BOARD_ID, post.boardId());
        assertEquals(AUTHOR_ID, post.authorId());
        assertEquals("Readable topic", post.title());
        assertEquals("This body is visible to everyone.", post.content());
        assertEquals(PostStatus.PUBLISHED, post.status());
        assertEquals(NOW, post.createdAt());
    }

    @Test
    @DisplayName("rejects an unknown post")
    void rejectsUnknownPost() {
        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.getPost(UNKNOWN_POST_ID));
    }

    @Test
    @DisplayName("treats a hidden post as not found")
    void rejectsHiddenPost() {
        PostDetail post = createPost("Post to hide", "This body will be hidden by governance.");
        service.hidePost(post.postId(), "off-topic");

        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.getPost(post.postId()));
    }

    @Test
    @DisplayName("treats a deleted post as not found")
    void rejectsDeletedPost() {
        Post post = seedPost(101L, BOARD_ID, AUTHOR_ID, "Deleted topic", "Body of a deleted post.",
                NOW.minusDays(1));
        forcePostStatus(post, PostStatus.DELETED);

        assertBizError(ErrorCode.POST_NOT_FOUND, () -> service.getPost(post.getPostId()));
    }
}
