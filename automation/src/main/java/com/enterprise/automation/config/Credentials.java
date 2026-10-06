package com.enterprise.automation.config;

/**
 * A username and password for the application under test.
 *
 * <p>{@link #toString()} masks the password, so credentials can never leak into logs or reports
 * by accident (a record would otherwise print every field).
 *
 * @param username login name
 * @param password secret; comes from configuration, an environment variable or a CI secret
 */
public record Credentials(String username, String password) {

    @Override
    public String toString() {
        // Overrides the record's generated toString, which would print the password
        return "Credentials[username=" + username + ", password=****]";
    }
}
