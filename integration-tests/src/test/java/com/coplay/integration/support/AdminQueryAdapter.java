package com.coplay.integration.support;

import com.coplay.user.api.UserService;

/**
 * Adapter from the user module to governance's administrator check.
 */
public final class AdminQueryAdapter implements com.coplay.governance.api.AdminQueryService {

    private final UserService userService;

    public AdminQueryAdapter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean isAdmin(long userId) {
        return userService.isAdmin(userId);
    }
}
