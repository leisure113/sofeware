package com.coplay.content;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.CommentStatus;
import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.CreatePostCommand;
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
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared fixture for the focused content service test classes.
 *
 * <p>Every test gets a fresh in-memory repository, fresh fakes and a fixed clock,
 * so tests are deterministic and independent. Boards 1/2 belong to the enabled
 * game 100, board 3 is disabled and board 4 belongs to the disabled game 200.</p>
 */
abstract class ContentServiceTestSupport {

    protected static final long BOARD_ID = 1L;
    protected static final long SECOND_BOARD_ID = 2L;
    protected static final long DISABLED_BOARD_ID = 3L;
    protected static final long DISABLED_GAME_BOARD_ID = 4L;
    protected static final long AUTHOR_ID = 2L;
    protected static final long OTHER_USER_ID = 3L;
    protected static final long UNKNOWN_USER_ID = 99L;
    protected static final long UNKNOWN_POST_ID = 9_999L;
    protected static final long UNKNOWN_COMMENT_ID = 8_888L;

    protected static final Instant FIXED_INSTANT = Instant.parse("2026-11-01T00:00:00Z");
    protected static final ZoneOffset ZONE = ZoneOffset.UTC;
    protected static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZONE);
    protected static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZONE);

    protected InMemoryContentRepository repository;
    protected FakeUserQueryService users;
    protected FakeGameQueryService games;
    protected FakeSensitiveWordFilter sensitiveWords;
    protected FakeNotificationService notifications;
    protected ContentServiceImpl service;

    @BeforeEach
    void setUpContentModule() {
        repository = new InMemoryContentRepository();
        repository.saveBoard(new Board(BOARD_ID, 100L, "General discussion"));
        repository.saveBoard(new Board(SECOND_BOARD_ID, 100L, "Guides and tips"));
        Board disabledBoard = new Board(DISABLED_BOARD_ID, 100L, "Archive");
        disabledBoard.disable();
        repository.saveBoard(disabledBoard);
        repository.saveBoard(new Board(DISABLED_GAME_BOARD_ID, 200L, "Retired game board"));

        users = new FakeUserQueryService();
        games = new FakeGameQueryService();
        sensitiveWords = new FakeSensitiveWordFilter();
        notifications = new FakeNotificationService();
        service = new ContentServiceImpl(
                repository, users, games, sensitiveWords, notifications, CLOCK);
    }

    protected PostDetail createPost(String title, String content) {
        return createPost(BOARD_ID, AUTHOR_ID, title, content);
    }

    protected PostDetail createPost(long boardId, long authorId, String title, String content) {
        return service.createPost(new CreatePostCommand(boardId, authorId, title, content));
    }

    protected Post seedPost(long postId,
                            long boardId,
                            long authorId,
                            String title,
                            String content,
                            LocalDateTime createdAt) {
        Post post = new Post(postId, boardId, authorId, title, content, createdAt);
        repository.savePost(post);
        return post;
    }

    protected Comment seedComment(long commentId,
                                  long postId,
                                  long authorId,
                                  String content,
                                  LocalDateTime createdAt) {
        Comment comment = new Comment(commentId, postId, authorId, content, createdAt);
        repository.saveComment(comment);
        return comment;
    }

    protected static BizException assertBizError(ErrorCode expected, Executable action) {
        BizException exception = assertThrows(BizException.class, action);
        assertEquals(expected.getCode(), exception.getCode(),
                () -> "expected " + expected.getCode() + " (" + expected + ") but got "
                        + exception.getCode() + ": " + exception.getMessage());
        return exception;
    }

    /**
     * The domain only exposes {@code hide()}; DELETED is reserved for the governance
     * adapter, so a test that needs the DELETED read path forces the state via reflection.
     */
    protected static void forcePostStatus(Post post, PostStatus status) {
        forceStatus(Post.class, post, status);
    }

    protected static void forceCommentStatus(Comment comment, CommentStatus status) {
        forceStatus(Comment.class, comment, status);
    }

    private static void forceStatus(Class<?> type, Object target, Object status) {
        try {
            Field field = type.getDeclaredField("status");
            field.setAccessible(true);
            field.set(target, status);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set status on " + type.getSimpleName(), exception);
        }
    }
}
