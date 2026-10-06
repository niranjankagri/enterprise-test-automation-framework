package com.enterprise.automation.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Browsers the framework can drive, locally or on a Grid. */
public enum BrowserType {
    CHROME,
    FIREFOX,
    EDGE;

    /**
     * Parses a configuration value such as {@code "chrome"} or {@code "Edge"}.
     *
     * @throws IllegalArgumentException with the list of valid values, so a typo in
     *                                  {@code -Dbrowser=...} fails fast with a clear message
     */
    public static BrowserType from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown browser '" + value + "'. Valid values: " + validValues(), e);
        }
    }

    private static String validValues() {
        return Arrays.stream(values()).map(b -> b.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }
}
