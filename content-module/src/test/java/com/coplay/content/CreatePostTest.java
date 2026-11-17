package com.coplay.content;

import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("createPost: validation, boundaries and publication")
class CreatePostTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("creates a PUBLISHED post, trims both texts, persists it and sends no notification")
    void createsPublishedPost() {
        PostDetail post = createPost("  Best jungle routes  ", "  Share your favorite routes.  ");

        assertEquals(PostStatus.PUBLISHED, post.status());
        assertEquals("Best jungle routes", post.title());
        assertEquals("Share your favorite routes.", post.content());
        assertEquals(BOARD_ID, post.boardId());
        assertEquals(AUTHOR_ID, post.authorId());
        assertEquals(NOW, post.createdAt());
        assertTrue(notifications.notifications().isEmpty());

        Post stored = repository.findPostById(post.postId()).orElseThrow();
        assertEquals(post.postId(), stored.getPostId());
        assertEquals("Best jungle routes", stored.getTitle());
        assertEquals("Share your favorite routes.", stored.getContent());
        assertEquals(PostStatus.PUBLISHED, stored.getStatus());
        assertEquals(NOW, stored.getCreatedAt());
        assertEquals(post.postId(), service.getPost(post.postId()).postId());
    }

    @Test
    @DisplayName("assigns strictly increasing post ids")
    void assignsIncreasingPostIds() {
        PostDetail first = createPost("First topic", "Body of the first topic.");
        PostDetail second = createPost("Second topic", "Body of the second topic.");

        assertTrue(second.postId() > first.postId());
    }

    @ParameterizedTest(name = "title with {0} characters is accepted")
    @ValueSource(ints = {3, 100})
    void acceptsTitleAtBoundaries(int length) {
        String title = "t".repeat(length);

        PostDetail post = createPost(title, "A valid body.");

        assertEquals(title, post.title());
        assertEquals(length, post.title().length());
    }

    @ParameterizedTest(name = "title with {0} characters is rejected")
    @ValueSource(ints = {0, 1, 2, 101, 200})
    void rejectsTitleOutsideBoundaries(int length) {
        String title = "t".repeat(length);

        assertBizError(ErrorCode.INVALID_TITLE, () -> createPost(title, "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a null title")
    void rejectsNullTitle() {
        assertBizError(ErrorCode.INVALID_TITLE, () -> createPost(null, "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a whitespace-only title even though it is longer than three characters")
    void rejectsBlankTitle() {
        assertBizError(ErrorCode.INVALID_TITLE, () -> createPost("    ", "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @ParameterizedTest(name = "content with {0} characters is accepted")
    @ValueSource(ints = {1, 5000})
    void acceptsContentAtBoundaries(int length) {
        String content = "c".repeat(length);

        PostDetail post = createPost("Valid title", content);

        assertEquals(content, post.content());
        assertEquals(length, post.content().length());
    }

    @ParameterizedTest(name = "content with {0} characters is rejected")
    @ValueSource(ints = {0, 5001, 6000})
    void rejectsContentOutsideBoundaries(int length) {
        String content = "c".repeat(length);

        assertBizError(ErrorCode.INVALID_CONTENT, () -> createPost("Valid title", content));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a null content")
    void rejectsNullContent() {
        assertBizError(ErrorCode.INVALID_CONTENT, () -> createPost("Valid title", null));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @ParameterizedTest(name = "whitespace-only content \"{0}\" is rejected")
    @ValueSource(strings = {" ", "   ", "\n\t "})
    void rejectsBlankContent(String content) {
        assertBizError(ErrorCode.INVALID_CONTENT, () -> createPost("Valid title", content));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("keeps inner whitespace while trimming only the outer text")
    void keepsInnerWhitespace() {
        PostDetail post = createPost("  Line one  ", "  first line\n\nsecond line  ");

        assertEquals("Line one", post.title());
        assertEquals("first line\n\nsecond line", post.content());
    }

    @Test
    @DisplayName("rejects an unknown board")
    void rejectsUnknownBoard() {
        assertBizError(ErrorCode.BOARD_NOT_FOUND,
                () -> createPost(777L, AUTHOR_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(777L).isEmpty());
    }

    @Test
    @DisplayName("rejects a disabled board")
    void rejectsDisabledBoard() {
        assertBizError(ErrorCode.BOARD_DISABLED,
                () -> createPost(DISABLED_BOARD_ID, AUTHOR_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(DISABLED_BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a board whose game is disabled")
    void rejectsDisabledGame() {
        assertBizError(ErrorCode.GAME_DISABLED,
                () -> createPost(DISABLED_GAME_BOARD_ID, AUTHOR_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(DISABLED_GAME_BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects an inactive user")
    void rejectsInactiveUser() {
        users.deactivate(OTHER_USER_ID);

        assertBizError(ErrorCode.USER_NOT_ACTIVE,
                () -> createPost(BOARD_ID, OTHER_USER_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects an unknown user as inactive")
    void rejectsUnknownUser() {
        assertBizError(ErrorCode.USER_NOT_ACTIVE,
                () -> createPost(BOARD_ID, UNKNOWN_USER_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a muted user")
    void rejectsMutedUser() {
        users.mute(AUTHOR_ID);

        assertBizError(ErrorCode.USER_MUTED,
                () -> createPost(BOARD_ID, AUTHOR_ID, "Valid title", "A valid body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a sensitive title")
    void rejectsSensitiveTitle() {
        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> createPost("Forbidden tricks for ranked", "A perfectly normal body."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("rejects a sensitive body")
    void rejectsSensitiveBody() {
        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> createPost("Perfectly normal title", "This body contains forbidden words."));
        assertTrue(repository.findPostsByBoard(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("applies the sensitive word filter case-insensitively to both fields")
    void rejectsSensitiveTextRegardlessOfCase() {
        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> createPost("FORBIDDEN title", "A perfectly normal body."));
        assertBizError(ErrorCode.SENSITIVE_CONTENT,
                () -> createPost("Perfectly normal title", "A FORBIDDEN body."));
    }

    @Test
    @DisplayName("rejects a null command with a NullPointerException")
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> service.createPost(null));
    }
}
