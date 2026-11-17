package com.coplay.team.api;

/**
 * Notification port. The team module only knows this interface,
 * not the concrete in-app notification implementation.
 */
public interface NotificationService {

    void notify(long userId, String type, String content);
}
