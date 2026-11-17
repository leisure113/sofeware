package com.coplay.user.dto;

import com.coplay.user.domain.Role;
import com.coplay.user.domain.UserStatus;

/**
 * Read model of a user, safe to return to the caller (it never contains the password hash).
 */
public record UserProfile(long id,
                          String studentNo,
                          String email,
                          String nickname,
                          UserStatus status,
                          Role role) {
}
