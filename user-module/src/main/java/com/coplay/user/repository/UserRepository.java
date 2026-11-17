package com.coplay.user.repository;

import com.coplay.user.domain.User;

import java.util.Optional;

/**
 * Persistence port for users.
 */
public interface UserRepository {

    long nextId();

    User save(User user);

    Optional<User> findById(long userId);

    Optional<User> findByStudentNo(String studentNo);

    Optional<User> findByEmail(String email);
}
