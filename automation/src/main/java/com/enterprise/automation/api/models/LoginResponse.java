package com.enterprise.automation.api.models;

/** Successful login. {@link #toString()} masks the token. */
public record LoginResponse(String token, String tokenType, long expiresIn, String username, String fullName,
                            String role) {

    @Override
    public String toString() {
        return "LoginResponse[username=" + username + ", role=" + role + ", tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", token=****]";
    }
}
