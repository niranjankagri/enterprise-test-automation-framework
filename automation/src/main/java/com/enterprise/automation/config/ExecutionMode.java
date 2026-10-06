package com.enterprise.automation.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Where browsers run. */
public enum ExecutionMode {
    /** A browser on this machine, started through Selenium Manager. */
    LOCAL,
    /** A browser on a Selenium Grid (or standalone server), reached through RemoteWebDriver. */
    REMOTE,
    /** A browser in a cloud provider (BrowserStack), added in Milestone 9. */
    CLOUD;

    /** Parses a configuration value such as {@code "local"} or {@code "remote"}. */
    public static ExecutionMode from(String value) {
        try {
            // Case-insensitive match against the constants
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            // Fail fast with the list of valid values
            String valid = Arrays.stream(values()).map(m -> m.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Unknown execution mode '" + value + "'. Valid values: " + valid, e);
        }
    }
}
