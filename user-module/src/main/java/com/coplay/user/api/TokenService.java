package com.coplay.user.api;

/**
 * Token port used after a successful login.
 * The production implementation may issue JWT, the demo uses random UUID tokens.
 */
public interface TokenService {

    String issue(long userId);

    /**
     * Resolves the user id behind a token.
     *
     * @throws com.coplay.user.support.BizException with INVALID_TOKEN when the token is unknown
     */
    long verify(String token);
}
