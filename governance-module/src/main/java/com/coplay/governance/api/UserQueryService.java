package com.coplay.governance.api;

/**
 * Port to the user module: checks whether a user is active.
 */
public interface UserQueryService {

    boolean isActive(long userId);
}
