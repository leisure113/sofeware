package com.coplay.content.api;

/**
 * Read-only user data required by the content module.
 * In the full project this is implemented by an adapter over the user module.
 */
public interface UserQueryService {

    boolean isActive(long userId);

    boolean isMuted(long userId);
}
