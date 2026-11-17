package com.coplay.team.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory lock registry. One ReentrantLock per team id.
 */
public final class InMemoryTeamLockRegistry implements TeamLockRegistry {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T withLock(long teamId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(teamId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
