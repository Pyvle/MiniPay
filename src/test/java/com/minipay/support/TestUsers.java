package com.minipay.support;

import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserState;

public final class TestUsers {
    // Persistence fixture only; not a password hash for authentication tests.
    public static final String PASSWORD_HASH = "test-password-hash";

    private TestUsers() {}

    public static User create(String name, String email) {
        User user = new User(name, email);
        user.setPasswordHash(PASSWORD_HASH);
        user.setSystemRole(SystemRole.USER);
        user.setState(UserState.ACTIVE);
        return user;
    }
}
