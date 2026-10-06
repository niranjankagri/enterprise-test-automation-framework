package com.enterprise.automation.config;

import java.util.Locale;

/**
 * Every configuration key the framework reads, in one place.
 *
 * <p>Each key has two spellings: the property name used in {@code .properties} files and
 * {@code -D} system properties ({@code base.url}), and the environment-variable name used by CI
 * and Docker ({@code BASE_URL}).
 */
public enum ConfigKey {
    // Where the application under test is
    BASE_URL("base.url"),
    API_BASE_URL("api.base.url"),
    // Which browser, how and where it runs
    BROWSER("browser"),
    HEADLESS("headless"),
    EXECUTION("execution"),
    GRID_URL("grid.url"),
    // Browser window and timeouts
    WINDOW_WIDTH("window.width"),
    WINDOW_HEIGHT("window.height"),
    TIMEOUT_EXPLICIT_SECONDS("timeout.explicit.seconds"),
    TIMEOUT_PAGE_LOAD_SECONDS("timeout.page.load.seconds"),
    // Start the demo application in-process (local only)
    APP_AUTOSTART("app.autostart"),
    // Accounts of the application under test (passwords: secrets in real environments)
    ADMIN_USERNAME("admin.username"),
    ADMIN_PASSWORD("admin.password"),
    VIEWER_USERNAME("viewer.username"),
    VIEWER_PASSWORD("viewer.password"),
    // Direct database access (optional per environment)
    DB_URL("db.url"),
    DB_USERNAME("db.username"),
    DB_PASSWORD("db.password"),
    // Orchestration: parallelism and retries
    PARALLEL("parallel"),
    THREADS("threads"),
    RETRY_COUNT("retry.count");

    // Dotted name used in files and -D options
    private final String property;

    ConfigKey(String property) {
        this.property = property;
    }

    /** Name in properties files and {@code -D} options, e.g. {@code base.url}. */
    public String property() {
        return property;
    }

    /** Name as an environment variable, e.g. {@code BASE_URL}. */
    public String environmentVariable() {
        // "base.url" -> "BASE_URL": derived, so the two spellings can never drift apart
        return property.replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
