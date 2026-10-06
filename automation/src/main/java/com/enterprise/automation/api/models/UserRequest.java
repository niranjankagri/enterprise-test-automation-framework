package com.enterprise.automation.api.models;

import com.enterprise.automation.data.UserData;

/** Body of {@code POST /api/users}. {@link #toString()} masks the password. */
public record UserRequest(String username, String password, String fullName, String role) {

    public static UserRequest from(UserData user) {
        return new UserRequest(user.username(), user.password(), user.fullName(), user.role());
    }

    @Override
    public String toString() {
        return "UserRequest[username=" + username + ", fullName=" + fullName + ", role=" + role + ", password=****]";
    }
}
