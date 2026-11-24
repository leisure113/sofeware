package com.coplay.integration.support;

import com.coplay.user.api.UserService;
import com.coplay.user.domain.UserStatus;

/**
 * Adapter from user module to governance moderation port.
 * The fixed administrator id is the operator created by IntegrationFixture.
 */
public final class UserAdminAdapter implements com.coplay.governance.api.UserAdminService {

    private final UserService userService;
    private final long adminId;

    public UserAdminAdapter(UserService userService, long adminId) {
        this.userService = userService;
        this.adminId = adminId;
    }

    @Override
    public void muteUser(long userId, String reason) {
        userService.changeStatus(adminId, userId, UserStatus.MUTED);
    }

    @Override
    public void banUser(long userId, String reason) {
        userService.changeStatus(adminId, userId, UserStatus.BANNED);
    }
}
