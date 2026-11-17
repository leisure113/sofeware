package com.coplay.content;

import com.coplay.content.api.GameQueryService;
import com.coplay.content.api.NotificationService;
import com.coplay.content.api.SensitiveWordFilter;
import com.coplay.content.api.UserQueryService;
import com.coplay.content.fake.FakeGameQueryService;
import com.coplay.content.fake.FakeNotificationService;
import com.coplay.content.fake.FakeSensitiveWordFilter;
import com.coplay.content.fake.FakeUserQueryService;
import com.coplay.content.repository.ContentRepository;
import com.coplay.content.repository.InMemoryContentRepository;
import com.coplay.content.service.ContentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ContentServiceImpl: constructor and command contracts")
class ContentServiceContractTest {

    private final ContentRepository repository = new InMemoryContentRepository();
    private final UserQueryService users = new FakeUserQueryService();
    private final GameQueryService games = new FakeGameQueryService();
    private final SensitiveWordFilter sensitiveWords = new FakeSensitiveWordFilter();
    private final NotificationService notifications = new FakeNotificationService();
    private final Clock clock = ContentServiceTestSupport.CLOCK;

    @Test
    @DisplayName("accepts all non-null collaborators")
    void acceptsAllCollaborators() {
        assertDoesNotThrow(() -> new ContentServiceImpl(
                repository, users, games, sensitiveWords, notifications, clock));
    }

    @Test
    @DisplayName("rejects a null repository")
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                null, users, games, sensitiveWords, notifications, clock));
    }

    @Test
    @DisplayName("rejects a null user query service")
    void rejectsNullUserQueryService() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                repository, null, games, sensitiveWords, notifications, clock));
    }

    @Test
    @DisplayName("rejects a null game query service")
    void rejectsNullGameQueryService() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                repository, users, null, sensitiveWords, notifications, clock));
    }

    @Test
    @DisplayName("rejects a null sensitive word filter")
    void rejectsNullSensitiveWordFilter() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                repository, users, games, null, notifications, clock));
    }

    @Test
    @DisplayName("rejects a null notification service")
    void rejectsNullNotificationService() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                repository, users, games, sensitiveWords, null, clock));
    }

    @Test
    @DisplayName("rejects a null clock")
    void rejectsNullClock() {
        assertThrows(NullPointerException.class, () -> new ContentServiceImpl(
                repository, users, games, sensitiveWords, notifications, null));
    }
}
