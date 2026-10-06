package com.enterprise.automation.data;

/**
 * A back-office account. {@link #toString()} masks the password so it never reaches logs.
 *
 * @param username login name
 * @param password password
 * @param fullName display name
 * @param role     {@code ADMIN} or {@code VIEWER}
 */
public record UserData(String username, String password, String fullName, String role) {

    @Override
    public String toString() {
        return "UserData[username=" + username + ", fullName=" + fullName + ", role=" + role + ", password=****]";
    }
}
