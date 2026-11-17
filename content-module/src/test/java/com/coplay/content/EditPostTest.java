package com.coplay.content;

import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD tests for {@code ContentService.editPost}. They were written before the
 * feature existed and drove the red/green cycle recorded in the test summary.
 */
@DisplayName("editPost (TDD): author-only edit of PUBLISHED posts")
class EditPostTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("the author edits a published post and receives the updated detail")
    void authorEditsPublishedPost() {
        PostDetail post = createPost("Original title", "Original content.");

        PostDetail edited = service.editPost(
                post.postId(), AUTHOR_ID, "Revised title", "Revised content.");

        assertEquals(post.postId(), edited.postId());
        assertEquals(BOARD_ID, edited.boardId());
        assertEquals(AUTHOR_ID, edited.authorId());
        assertEquals("Revised title", edited.title());
        assertEquals("Revised content.", edited.content());
        assertEquals(PostStatus.PUBLISHED, edited.status());
        assertEquals(post.createdAt(), edited.createdAt());

        Post stored = repository.findPostById(post.postId()).orElseThrow();
        assertEquals("Revised title", stored.getTitle());
        assertEquals("Revised content.", stored.getContent());
        assertEquals(PostStatus.PUBLISHED, stored.getStatus());
        assertEquals(post.createdAt(), stored.getCreatedAt());

        assertEquals("Revised title", service.getPost(post.postId()).title());
        List<PostBrief> posts = service.listPosts(BOARD_ID);
        assertEquals(1, posts.size());
        assertEquals("Revised title", posts.get(0).title());
        assertEquals(post.postId(), posts.get(0).postId());
    }

    @Test
    @DisplayName("trims the title and content before saving the edit")
    void trimsTitleAndContent() {
        PostDetail post = createPost("Original title", "Original content.");

        PostDetail edited = service.editPost(
                post.postId(), AUTHOR_ID, "  Revised title  ", "  Revised content.  ");

        assertEquals("Revised title", edited.title());
        assertEquals("Revised content.", edited.content());
        assertEquals("Revised title", repository.findPostById(post.postId()).orElseThrow().getTitle());
        assertEquals("Revised content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @ParameterizedTest(name = "title with {0} characters is accepted")
    @ValueSource(ints = {3, 100})
    void acceptsTitleAtBoundaries(int length) {
        PostDetail post = createPost("Original title", "Original content.");
        String title = "t".repeat(length);

        PostDetail edited = service.editPost(post.postId(), AUTHOR_ID, title, "Revised content.");

        assertEquals(title, edited.title());
        assertEquals(length, edited.title().length());
    }

    @ParameterizedTest(name = "title with {0} characters is rejected")
    @ValueSource(ints = {0, 1, 2, 101, 200})
    void rejectsTitleOutsideBoundaries(int length) {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_TITLE,
                () -> service.editPost(post.postId(), AUTHOR_ID, "t".repeat(length), "Revised content."));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("rejects a null title")
    void rejectsNullTitle() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_TITLE,
                () -> service.editPost(post.postId(), AUTHOR_ID, null, "Revised content."));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
    }

    @Test
    @DisplayName("rejects a whitespace-only title")
    void rejectsBlankTitle() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_TITLE,
                () -> service.editPost(post.postId(), AUTHOR_ID, "    ", "Revised content."));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
    }

    @ParameterizedTest(name = "content with {0} characters is accepted")
    @ValueSource(ints = {1, 5000})
    void acceptsContentAtBoundaries(int length) {
        PostDetail post = createPost("Original title", "Original content.");
        String content = "c".repeat(length);

        PostDetail edited = service.editPost(post.postId(), AUTHOR_ID, "Revised title", content);

        assertEquals(content, edited.content());
        assertEquals(length, edited.content().length());
    }

    @ParameterizedTest(name = "content with {0} characters is rejected")
    @ValueSource(ints = {0, 5001, 6000})
    void rejectsContentOutsideBoundaries(int length) {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Revised title", "c".repeat(length)));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("rejects a null content")
    void rejectsNullContent() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Revised title", null));
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @ParameterizedTest(name = "whitespace-only content \"{0}\" is rejected")
    @ValueSource(strings = {" ", "   ", "\n\t "})
    void rejectsBlankContent(String content) {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.INVALID_CONTENT,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Revised title", content));
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("rejects an editor who is not the post author")
    void rejectsNonAuthor() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.NOT_POST_AUTHOR,
                () -> service.editPost(post.postId(), OTHER_USER_ID, "Hijacked title", "Hijacked content."));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
        assertEquals(AUTHOR_ID, repository.findPostById(post.postId()).orElseThrow().getAuthorId());
    }

    @Test
    @DisplayName("rejects editing an unknown post")
    void rejectsUnknownPost() {
        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.editPost(UNKNOWN_POST_ID, AUTHOR_ID, "Revised title", "Revised content."));
    }

    @Test
    @DisplayName("rejects editing a hidden post, even for its author")
    void rejectsHiddenPost() {
        PostDetail post = createPost("Post to hide", "This post will be hidden before editing.");
        service.hidePost(post.postId(), "off-topic");

        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Revised title", "Revised content."));
        assertEquals(PostStatus.HIDDEN,
                repository.findPostById(post.postId()).orElseThrow().getStatus());
        assertEquals("Post to hide", repository.findPostById(post.postId()).orElseThrow().getTitle());
    }

    @Test
    @DisplayName("rejects editing a deleted post, even for its author")
    void rejectsDeletedPost() {
        Post post = seedPost(101L, BOARD_ID, AUTHOR_ID, "Deleted topic",
                "Body of a deleted post.", NOW.minusDays(1));
        forcePostStatus(post, PostStatus.DELETED);

        assertBizError(ErrorCode.POST_NOT_FOUND,
                () -> service.editPost(post.getPostId(), AUTHOR_ID, "Revised title", "Revised content."));
        assertEquals(PostStatus.DELETED, post.getStatus());
        assertEquals("Deleted topic", post.getTitle());
    }

    @Test
    @DisplayName("rejects a sensitive title")
    void rejectsSensitiveTitle() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Forbidden edit", "Revised content."));
        assertEquals("Original title", repository.findPostById(post.postId()).orElseThrow().getTitle());
    }

    @Test
    @DisplayName("rejects a sensitive body")
    void rejectsSensitiveContent() {
        PostDetail post = createPost("Original title", "Original content.");

        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> service.editPost(post.postId(), AUTHOR_ID, "Revised title",
                        "This body contains forbidden words."));
        assertEquals("Original content.",
                repository.findPostById(post.postId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("does not send any notification when a post is edited")
    void doesNotNotifyOnEdit() {
        PostDetail post = createPost("Original title", "Original content.");

        service.editPost(post.postId(), AUTHOR_ID, "Revised title", "Revised content.");

        assertTrue(notifications.notifications().isEmpty());
    }

    @Test
    @DisplayName("supports editing the same post more than once")
    void allowsSeveralEdits() {
        PostDetail post = createPost("Original title", "Original content.");

        service.editPost(post.postId(), AUTHOR_ID, "Second title", "Second content.");
        PostDetail edited = service.editPost(post.postId(), AUTHOR_ID, "Third title", "Third content.");

        assertEquals("Third title", edited.title());
        assertEquals("Third content.", edited.content());
        assertEquals(post.createdAt(), edited.createdAt());
        assertEquals(post.postId(), repository.findPostById(post.postId()).orElseThrow().getPostId());
        assertEquals("Third title", service.getPost(post.postId()).title());
    }
}
