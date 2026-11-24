package com.coplay.integration.support;

import com.coplay.user.api.UserService;

/**
 * Adapter from the user module read model to the three modules' user query ports.
 */
public final class UserQueryAdapter implements
        com.coplay.team.api.UserQueryService,
        com.coplay.content.api.UserQueryService,
        com.coplay.governance.api.UserQueryService {

    private final UserService userService;

    public UserQueryAdapter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean isActive(long userId) {
        return userService.isActive(userId);
    }

    @Override
    public boolean isMuted(long userId) {
        return userService.isMuted(userId);
    }
}
