package com.coplay.governance.api;

/**
 * Port to the user module: moderation actions on user accounts.
 */
public interface UserAdminService {

    void muteUser(long userId, String reason);

    void banUser(long userId, String reason);
}
