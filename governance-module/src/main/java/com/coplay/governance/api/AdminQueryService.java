package com.coplay.governance.api;

/**
 * Port to the user/role module: checks whether a user is a platform administrator.
 */
public interface AdminQueryService {

    boolean isAdmin(long userId);
}
