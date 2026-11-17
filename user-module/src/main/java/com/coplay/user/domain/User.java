package com.coplay.user.domain;

import java.util.Objects;

/**
 * User aggregate root.
 *
 * <p>Account status is changed through {@link #activate()} and
 * {@link #changeStatus(UserStatus)} so the service can keep the rules in one place.</p>
 */
public final class User {

    private final long id;
    private final String studentNo;
    private final String email;
    private final String passwordHash;
    private final String nickname;
    private UserStatus status;
    private final Role role;

    public User(long id,
                String studentNo,
                String email,
                String passwordHash,
                String nickname,
                UserStatus status,
                Role role) {
        this.id = id;
        this.studentNo = Objects.requireNonNull(studentNo, "studentNo");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.status = Objects.requireNonNull(status, "status");
        this.role = Objects.requireNonNull(role, "role");
    }

    public long getId() {
        return id;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }

    /**
     * Marks the account as campus-verified.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Applies an administrative status change.
     */
    public void changeStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isMuted() {
        return status == UserStatus.MUTED;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
