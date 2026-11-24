package com.coplay.integration.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One notification adapter implementing the notification ports of all modules.
 */
public final class NotificationAdapter implements
        com.coplay.team.api.NotificationService,
        com.coplay.content.api.NotificationService,
        com.coplay.governance.api.NotificationService {

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    @Override
    public void notify(long userId, String type, String content) {
        notifications.add(new Notification(userId, type, content));
    }

    public List<Notification> all() {
        return List.copyOf(notifications);
    }

    public long countByType(String type) {
        return notifications.stream().filter(item -> item.type().equals(type)).count();
    }

    public record Notification(long userId, String type, String content) {
    }
}
