package com.coplay.content.fake;

import com.coplay.content.api.NotificationService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeNotificationService implements NotificationService {

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    @Override
    public void notify(long userId, String type, String content) {
        notifications.add(new Notification(userId, type, content));
    }

    public List<Notification> notifications() {
        return List.copyOf(notifications);
    }

    public long countByType(String type) {
        return notifications.stream().filter(notification -> notification.type().equals(type)).count();
    }

    public record Notification(long userId, String type, String content) {
    }
}
