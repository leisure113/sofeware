package com.coplay.team.support;

import java.util.function.Supplier;

/**
 * Per-team lock used to make capacity checks and approvals atomic.
 */
public interface TeamLockRegistry {

    <T> T withLock(long teamId, Supplier<T> action);
}
