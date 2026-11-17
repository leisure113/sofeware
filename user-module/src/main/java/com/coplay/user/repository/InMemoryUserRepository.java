package com.coplay.user.repository;

import com.coplay.user.domain.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory user repository used by the standalone demo and tests.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);

    @Override
    public long nextId() {
        long candidate = ids.incrementAndGet();
        while (users.containsKey(candidate)) {
            candidate = ids.incrementAndGet();
        }
        return candidate;
    }

    @Override
    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<User> findByStudentNo(String studentNo) {
        return users.values().stream()
                .filter(user -> user.getStudentNo().equals(studentNo))
                .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }
}
