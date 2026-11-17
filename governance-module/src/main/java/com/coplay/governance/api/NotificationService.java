package com.coplay.governance.api;

/**
 * Port to the notification module.
 */
public interface NotificationService {

    void notify(long userId, String type, String content);
}
