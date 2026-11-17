package com.coplay.user.support;

import com.coplay.user.api.TokenService;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token store: random UUID token -> user id.
 * The production implementation would issue and verify JWTs instead.
 */
public final class InMemoryTokenService implements TokenService {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    @Override
    public String issue(long userId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, userId);
        return token;
    }

    @Override
    public long verify(String token) {
        Long userId = token == null ? null : tokens.get(token);
        if (Objects.isNull(userId)) {
            throw new BizException(ErrorCode.INVALID_TOKEN);
        }
        return userId;
    }
}
