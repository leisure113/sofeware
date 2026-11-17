package com.coplay.user.api;

import com.coplay.user.domain.UserStatus;
import com.coplay.user.dto.LoginCommand;
import com.coplay.user.dto.LoginResult;
import com.coplay.user.dto.RegisterCommand;
import com.coplay.user.dto.UserProfile;
import com.coplay.user.dto.VerifyCampusCommand;

/**
 * Public use cases of the user module.
 */
public interface UserService {

    UserProfile register(RegisterCommand command);

    void sendVerificationCode(String email);

    UserProfile verifyCampus(VerifyCampusCommand command);

    LoginResult login(LoginCommand command);

    /**
     * Changes the password of an existing user after verifying the old password.
     *
     * @throws com.coplay.user.support.BizException with USER_NOT_FOUND when the user does not exist,
     *         INVALID_OLD_PASSWORD when the old password does not match and
     *         WEAK_PASSWORD when the new password is shorter than 8 characters
     */
    void changePassword(long userId, String oldPassword, String newPassword);

    UserProfile getProfile(long userId);

    void changeStatus(long operatorId, long userId, UserStatus status);

    boolean isActive(long userId);

    boolean isMuted(long userId);

    boolean isAdmin(long userId);
}
