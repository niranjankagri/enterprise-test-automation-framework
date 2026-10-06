package com.enterprise.automation.api.models;

import com.enterprise.automation.config.Credentials;

/** Body of {@code POST /api/auth/login}. {@link #toString()} masks the password. */
public record LoginRequest(String username, String password) {

    public static LoginRequest of(Credentials credentials) {
        return new LoginRequest(credentials.username(), credentials.password());
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
