package com.coplay.content;

import com.coplay.content.api.GameQueryService;
import com.coplay.content.api.NotificationService;
import com.coplay.content.api.SensitiveWordFilter;
import com.coplay.content.api.UserQueryService;
import com.coplay.content.domain.Board;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.repository.InMemoryContentRepository;
import com.coplay.content.service.ContentServiceImpl;
import com.coplay.content.support.BizException;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Standalone demo: seeded boards -> publish -> comment -> reject muted/sensitive
 * -> hide post -> hidden post is not readable.
 * Run with: java -jar target/content-module-1.0.0.jar
 */
public final class ContentModuleDemo {

    private ContentModuleDemo() {
    }

    public static void main(String[] args) {
        InMemoryContentRepository repository = new InMemoryContentRepository();
        repository.saveBoard(new Board(1L, 100L, "General discussion"));
        repository.saveBoard(new Board(2L, 100L, "Guides and tips"));
        repository.saveBoard(new Board(3L, 100L, "Archive", false));

        DemoUsers users = new DemoUsers();
        DemoGames games = new DemoGames();
        DemoSensitiveWords sensitiveWords = new DemoSensitiveWords();
        DemoNotifications notifications = new DemoNotifications();

        ContentServiceImpl service = new ContentServiceImpl(
                repository,
                users,
                games,
                sensitiveWords,
                notifications,
                Clock.systemDefaultZone());

        PostDetail welcome = service.createPost(new CreatePostCommand(
                1L,
                2L,
                "Welcome to the CoPlay content module",
                "Share game guides, patch notes and friendly discussions here."));
        System.out.println("Created post: " + welcome.postId()
                + ", board=" + welcome.boardId()
                + ", status=" + welcome.status());

        PostDetail guide = service.createPost(new CreatePostCommand(
                2L,
                1L,
                "Jungle routes for the new season",
                "Three early routes that keep your clear fast and safe."));
        System.out.println("Created post: " + guide.postId()
                + ", board=" + guide.boardId()
                + ", status=" + guide.status());

        List<Long> visible = service.listPosts(1L).stream()
                .map(post -> post.postId())
                .collect(Collectors.toList());
        System.out.println("Published posts in board 1: " + visible);

        CommentBrief comment = service.addComment(new CreateCommentCommand(
                welcome.postId(),
                1L,
                "Thanks for the guide, the second route works well."));
        System.out.println("Comment: " + comment.commentId()
                + ", comments on post " + comment.postId() + "="
                + service.listComments(welcome.postId()).size());

        try {
            service.addComment(new CreateCommentCommand(welcome.postId(), 3L, "Muted user comment"));
        } catch (BizException exception) {
            System.out.println("Muted user rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            service.createPost(new CreatePostCommand(1L, 2L, "Cheat engine tutorial", "Download the hack here."));
        } catch (BizException exception) {
            System.out.println("Sensitive post rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        try {
            service.createPost(new CreatePostCommand(3L, 2L, "Old board topic", "This board is disabled."));
        } catch (BizException exception) {
            System.out.println("Disabled board rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        service.hidePost(welcome.postId(), "off-topic");
        System.out.println("After hide: visible posts in board 1=" + service.listPosts(1L).size());

        try {
            service.getPost(welcome.postId());
        } catch (BizException exception) {
            System.out.println("Hidden post rejected: " + exception.getCode()
                    + " / " + exception.getMessage());
        }

        System.out.println("Notifications:");
        notifications.messages().forEach(message -> System.out.println("  - " + message));
    }

    private static final class DemoUsers implements UserQueryService {
        @Override
        public boolean isActive(long userId) {
            return userId == 1L || userId == 2L || userId == 3L;
        }

        @Override
        public boolean isMuted(long userId) {
            return userId == 3L;
        }
    }

    private static final class DemoGames implements GameQueryService {
        @Override
        public boolean isGameEnabled(long gameId) {
            return gameId == 100L;
        }
    }

    private static final class DemoSensitiveWords implements SensitiveWordFilter {
        private final Set<String> blockedWords = Set.of("cheat", "hack");

        @Override
        public boolean isBlocked(String text) {
            if (text == null) {
                return false;
            }
            String lowerCase = text.toLowerCase(Locale.ROOT);
            return blockedWords.stream().anyMatch(lowerCase::contains);
        }
    }

    private static final class DemoNotifications implements NotificationService {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void notify(long userId, String type, String content) {
            messages.add("userId=" + userId + ", type=" + type + ", content=" + content);
        }

        List<String> messages() {
            return messages;
        }
    }
}
