package com.enterprise.automation.config;

/**
 * Where the application's database is, if this environment allows direct access.
 * {@link #toString()} masks the password.
 *
 * @param url      JDBC URL, e.g. {@code jdbc:h2:tcp://localhost:9092/mem:shop}
 * @param username database user
 * @param password database password (may be empty)
 */
public record DatabaseConfig(String url, String username, String password) {

    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", username=" + username + ", password=****]";
    }
}
