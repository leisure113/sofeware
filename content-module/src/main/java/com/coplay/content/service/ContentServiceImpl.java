package com.coplay.content.service;

import com.coplay.content.api.ContentService;
import com.coplay.content.api.GameQueryService;
import com.coplay.content.api.NotificationService;
import com.coplay.content.api.SensitiveWordFilter;
import com.coplay.content.api.UserQueryService;
import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.Post;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.repository.ContentRepository;
import com.coplay.content.support.BizException;
import com.coplay.content.support.ErrorCode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Content module implementation.
 *
 * <p>The service only depends on ports (user, game, sensitive word filter,
 * notification and repository), so it can be tested and run without Spring or MySQL.</p>
 */
public final class ContentServiceImpl implements ContentService {

    private static final int MIN_TITLE = 3;
    private static final int MAX_TITLE = 100;
    private static final int MAX_POST_CONTENT = 5000;
    private static final int MAX_COMMENT_CONTENT = 500;
    private static final String NOTIFICATION_POST_COMMENT = "POST_COMMENT";
    private static final String NOTIFICATION_CONTENT_HIDDEN = "CONTENT_HIDDEN";
    private static final Comparator<Post> NEWEST_POST_FIRST =
            Comparator.comparing(Post::getCreatedAt)
                    .thenComparingLong(Post::getPostId)
                    .reversed();
    private static final Comparator<Comment> NEWEST_COMMENT_FIRST =
            Comparator.comparing(Comment::getCreatedAt)
                    .thenComparingLong(Comment::getCommentId)
                    .reversed();

    private final ContentRepository repository;
    private final UserQueryService userQueryService;
    private final GameQueryService gameQueryService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final NotificationService notificationService;
    private final Clock clock;

    public ContentServiceImpl(ContentRepository repository,
                              UserQueryService userQueryService,
                              GameQueryService gameQueryService,
                              SensitiveWordFilter sensitiveWordFilter,
                              NotificationService notificationService,
                              Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.userQueryService = Objects.requireNonNull(userQueryService, "userQueryService");
        this.gameQueryService = Objects.requireNonNull(gameQueryService, "gameQueryService");
        this.sensitiveWordFilter = Objects.requireNonNull(sensitiveWordFilter, "sensitiveWordFilter");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostDetail createPost(CreatePostCommand command) {
        Objects.requireNonNull(command, "command");
        requireActiveUser(command.authorId());
        Board board = requireBoard(command.boardId());
        if (!board.isEnabled()) {
            throw new BizException(ErrorCode.BOARD_DISABLED);
        }
        if (!gameQueryService.isGameEnabled(board.getGameId())) {
            throw new BizException(ErrorCode.GAME_DISABLED);
        }
        String title = validateTitle(command.title());
        String content = validatePostContent(command.content());
        requireCleanText(title + "\n" + content);

        Post post = new Post(
                repository.nextPostId(),
                board.getBoardId(),
                command.authorId(),
                title,
                content,
                now());
        repository.savePost(post);
        return toDetail(post);
    }

    @Override
    public PostDetail getPost(long postId) {
        Post post = requirePost(postId);
        if (!post.isPublished()) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return toDetail(post);
    }

    @Override
    public PostDetail editPost(long postId, long authorId, String title, String content) {
        Post post = requirePost(postId);
        if (!post.isPublished()) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getAuthorId() != authorId) {
            throw new BizException(ErrorCode.NOT_POST_AUTHOR);
        }
        String validatedTitle = validateTitle(title);
        String validatedContent = validatePostContent(content);
        requireCleanText(validatedTitle + "\n" + validatedContent);
        post.edit(validatedTitle, validatedContent);
        repository.savePost(post);
        return toDetail(post);
    }

    @Override
    public List<PostBrief> listPosts(long boardId) {
        requireBoard(boardId);
        return repository.findPostsByBoard(boardId).stream()
                .filter(Post::isPublished)
                .sorted(NEWEST_POST_FIRST)
                .map(this::toBrief)
                .toList();
    }

    @Override
    public CommentBrief addComment(CreateCommentCommand command) {
        Objects.requireNonNull(command, "command");
        Post post = requirePost(command.postId());
        if (!post.isPublished()) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        requireActiveUser(command.authorId());
        String content = validateCommentContent(command.content());
        requireCleanText(content);

        Comment comment = new Comment(
                repository.nextCommentId(),
                post.getPostId(),
                command.authorId(),
                content,
                now());
        repository.saveComment(comment);
        notificationService.notify(
                post.getAuthorId(),
                NOTIFICATION_POST_COMMENT,
                "New comment on your post " + post.getPostId()
                        + ", comment id " + comment.getCommentId());
        return toBrief(comment);
    }

    @Override
    public List<CommentBrief> listComments(long postId) {
        requirePost(postId);
        return repository.findCommentsByPost(postId).stream()
                .filter(Comment::isPublished)
                .sorted(NEWEST_COMMENT_FIRST)
                .map(this::toBrief)
                .toList();
    }

    @Override
    public void hidePost(long postId, String reason) {
        Post post = requirePost(postId);
        if (!post.isPublished()) {
            return;
        }
        post.hide();
        repository.savePost(post);
        notificationService.notify(
                post.getAuthorId(),
                NOTIFICATION_CONTENT_HIDDEN,
                "Your post " + post.getPostId() + " was hidden" + describeReason(reason));
    }

    @Override
    public void hideComment(long commentId, String reason) {
        Comment comment = requireComment(commentId);
        if (!comment.isPublished()) {
            return;
        }
        comment.hide();
        repository.saveComment(comment);
        notificationService.notify(
                comment.getAuthorId(),
                NOTIFICATION_CONTENT_HIDDEN,
                "Your comment " + comment.getCommentId() + " was hidden" + describeReason(reason));
    }

    private void requireActiveUser(long userId) {
        if (userQueryService.isMuted(userId)) {
            throw new BizException(ErrorCode.USER_MUTED);
        }
        if (!userQueryService.isActive(userId)) {
            throw new BizException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    private Board requireBoard(long boardId) {
        return repository.findBoardById(boardId)
                .orElseThrow(() -> new BizException(ErrorCode.BOARD_NOT_FOUND));
    }

    private Post requirePost(long postId) {
        return repository.findPostById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    private Comment requireComment(long commentId) {
        return repository.findCommentById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private String validateTitle(String title) {
        if (title == null) {
            throw new BizException(ErrorCode.INVALID_TITLE);
        }
        String trimmed = title.trim();
        if (trimmed.length() < MIN_TITLE || trimmed.length() > MAX_TITLE) {
            throw new BizException(ErrorCode.INVALID_TITLE);
        }
        return trimmed;
    }

    private String validatePostContent(String content) {
        if (content == null) {
            throw new BizException(ErrorCode.INVALID_CONTENT);
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_POST_CONTENT) {
            throw new BizException(ErrorCode.INVALID_CONTENT);
        }
        return trimmed;
    }

    private String validateCommentContent(String content) {
        if (content == null) {
            throw new BizException(ErrorCode.INVALID_CONTENT);
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_COMMENT_CONTENT) {
            throw new BizException(ErrorCode.INVALID_CONTENT);
        }
        return trimmed;
    }

    private void requireCleanText(String text) {
        if (sensitiveWordFilter.isBlocked(text)) {
            throw new BizException(ErrorCode.SENSITIVE_CONTENT);
        }
    }

    private String describeReason(String reason) {
        return reason == null || reason.isBlank() ? "" : ", reason: " + reason.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private PostBrief toBrief(Post post) {
        return new PostBrief(
                post.getPostId(),
                post.getBoardId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getStatus(),
                post.getCreatedAt());
    }

    private PostDetail toDetail(Post post) {
        return new PostDetail(
                post.getPostId(),
                post.getBoardId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                post.getStatus(),
                post.getCreatedAt());
    }

    private CommentBrief toBrief(Comment comment) {
        return new CommentBrief(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt());
    }
}
