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

    UserProfile getProfile(long userId);

    void changeStatus(long operatorId, long userId, UserStatus status);

    boolean isActive(long userId);

    boolean isMuted(long userId);

    boolean isAdmin(long userId);
}
