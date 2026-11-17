package com.coplay.content;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.CommentStatus;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.fake.FakeGameQueryService;
import com.coplay.content.fake.FakeNotificationService;
import com.coplay.content.fake.FakeSensitiveWordFilter;
import com.coplay.content.fake.FakeUserQueryService;
import com.coplay.content.repository.InMemoryContentRepository;
import com.coplay.content.service.ContentServiceImpl;
import com.coplay.content.support.BizException;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private InMemoryContentRepository repository;
    private FakeUserQueryService users;
    private FakeGameQueryService games;
    private FakeSensitiveWordFilter sensitiveWords;
    private FakeNotificationService notifications;
    private ContentServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryContentRepository();
        repository.saveBoard(new Board(1L, 100L, "General discussion"));
        repository.saveBoard(new Board(2L, 100L, "Guides and tips"));
        Board archive = new Board(3L, 100L, "Archive");
        archive.disable();
        repository.saveBoard(archive);
        repository.saveBoard(new Board(4L, 200L, "Retired game board"));

        users = new FakeUserQueryService();
        games = new FakeGameQueryService();
        sensitiveWords = new FakeSensitiveWordFilter();
        notifications = new FakeNotificationService();
        service = new ContentServiceImpl(
                repository, users, games, sensitiveWords, notifications, CLOCK);
    }

    @Test
    @DisplayName("create post trims the title, publishes it and returns the detail")
    void createPostSuccess() {
        PostDetail post = service.createPost(new CreatePostCommand(
                1L, 2L, "  Best jungle routes  ", "Share your favorite routes for the new season."));

        assertEquals(PostStatus.PUBLISHED, post.status());
        assertEquals("Best jungle routes", post.title());
        assertEquals(1L, post.boardId());
        assertEquals(2L, post.authorId());
        assertEquals(NOW, post.createdAt());
        assertEquals(post.postId(), service.getPost(post.postId()).postId());
        assertEquals(post.postId(), repository.findPostById(post.postId()).orElseThrow().getPostId());
    }

    @Test
    @DisplayName("create post rejects a title shorter than 3 characters")
    void createPostRejectsInvalidTitle() {
        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(1L, 2L, "ab", "Valid body text.")));

        assertEquals(ErrorCode.INVALID_TITLE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create post rejects an inactive user")
    void createPostRejectsInactiveUser() {
        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(1L, 9L, "Valid title", "Valid body text.")));

        assertEquals(ErrorCode.USER_NOT_ACTIVE.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create post rejects a muted user")
    void createPostRejectsMutedUser() {
        users.mute(2L);

        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(1L, 2L, "Valid title", "Valid body text.")));

        assertEquals(ErrorCode.USER_MUTED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create post rejects a disabled board")
    void createPostRejectsDisabledBoard() {
        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(3L, 2L, "Valid title", "Valid body text.")));

        assertEquals(ErrorCode.BOARD_DISABLED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create post rejects a board whose game is disabled")
    void createPostRejectsDisabledGame() {
        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(4L, 2L, "Valid title", "Valid body text.")));

        assertEquals(ErrorCode.GAME_DISABLED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("create post checks the sensitive word filter on the title as well")
    void createPostRejectsSensitiveTitle() {
        BizException exception = assertThrows(BizException.class,
                () -> service.createPost(new CreatePostCommand(
                        1L, 2L, "Forbidden tricks for ranked", "A perfectly normal body.")));

        assertEquals(ErrorCode.SENSITIVE_CONTENT.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("a hidden post cannot be read through getPost")
    void hiddenPostIsNotReadable() {
        PostDetail post = createPost("Post to hide", "This body will be hidden by governance.");

        service.hidePost(post.postId(), "off-topic");

        BizException exception = assertThrows(BizException.class, () -> service.getPost(post.postId()));
        assertEquals(ErrorCode.POST_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(PostStatus.HIDDEN,
                repository.findPostById(post.postId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("adding a comment stores it and notifies the post author")
    void addCommentNotifiesPostAuthor() {
        PostDetail post = createPost("Jungle guide", "A short guide for the new season.");

        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), 3L, "Great write-up, thanks for sharing!"));

        assertEquals(post.postId(), comment.postId());
        assertEquals(3L, comment.authorId());
        assertEquals(CommentStatus.PUBLISHED, comment.status());
        assertEquals(1, service.listComments(post.postId()).size());
        assertEquals(1L, notifications.countByType("POST_COMMENT"));

        FakeNotificationService.Notification notification = notifications.notifications().get(0);
        assertEquals(post.authorId(), notification.userId());
        assertEquals("POST_COMMENT", notification.type());
    }

    @Test
    @DisplayName("adding a comment to a hidden post is rejected")
    void addCommentRejectedOnHiddenPost() {
        PostDetail post = createPost("Hidden discussion", "The body of a post that will be hidden.");
        service.hidePost(post.postId(), "off-topic");

        BizException exception = assertThrows(BizException.class,
                () -> service.addComment(new CreateCommentCommand(post.postId(), 3L, "Too late")));

        assertEquals(ErrorCode.POST_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(0, service.listComments(post.postId()).size());
        assertEquals(0L, notifications.countByType("POST_COMMENT"));
    }

    @Test
    @DisplayName("listPosts returns only published posts of the board, newest first")
    void listPostsReturnsOnlyPublishedNewestFirst() {
        PostDetail first = createPost("First topic", "Body of the first topic.");
        PostDetail second = createPost("Second topic", "Body of the second topic.");
        PostDetail third = createPost("Third topic", "Body of the third topic.");
        service.createPost(new CreatePostCommand(2L, 1L, "Other board post", "Body of another board."));

        service.hidePost(second.postId(), "spam");

        List<PostBrief> posts = service.listPosts(1L);

        assertEquals(2, posts.size());
        assertEquals(List.of(third.postId(), first.postId()),
                posts.stream().map(PostBrief::postId).toList());
        assertTrue(posts.stream().allMatch(post -> post.status() == PostStatus.PUBLISHED));
    }

    @Test
    @DisplayName("hiding a post and a comment is idempotent and notifies each author once")
    void hidePostAndCommentAreIdempotent() {
        PostDetail post = createPost("Post to moderate", "This post will be hidden by governance.");
        CommentBrief comment = service.addComment(new CreateCommentCommand(
                post.postId(), 3L, "This comment will be hidden as well."));

        service.hidePost(post.postId(), "off-topic");
        service.hidePost(post.postId(), "off-topic again");
        service.hideComment(comment.commentId(), "rude");
        service.hideComment(comment.commentId(), "rude again");

        assertEquals(PostStatus.HIDDEN,
                repository.findPostById(post.postId()).orElseThrow().getStatus());
        assertEquals(CommentStatus.HIDDEN,
                repository.findCommentById(comment.commentId()).orElseThrow().getStatus());
        assertTrue(service.listPosts(1L).isEmpty());
        assertTrue(service.listComments(post.postId()).isEmpty());
        assertEquals(2L, notifications.countByType("CONTENT_HIDDEN"));
    }

    private PostDetail createPost(String title, String content) {
        return service.createPost(new CreatePostCommand(1L, 2L, title, content));
    }
}
