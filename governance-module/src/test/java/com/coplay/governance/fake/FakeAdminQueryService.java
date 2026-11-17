package com.coplay.governance.fake;

import com.coplay.governance.api.AdminQueryService;

import java.util.HashSet;
import java.util.Set;

public final class FakeAdminQueryService implements AdminQueryService {

    private final Set<Long> admins = new HashSet<>(Set.of(9L));

    public FakeAdminQueryService grant(long userId) {
        admins.add(userId);
        return this;
    }

    public FakeAdminQueryService revoke(long userId) {
        admins.remove(userId);
        return this;
    }

    @Override
    public boolean isAdmin(long userId) {
        return admins.contains(userId);
    }
}
