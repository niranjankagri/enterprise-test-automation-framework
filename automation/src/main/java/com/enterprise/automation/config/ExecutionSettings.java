package com.enterprise.automation.config;

import java.util.Locale;
import java.util.Set;

/**
 * How a run executes.
 *
 * @param parallel   TestNG parallel mode: {@code none}, {@code classes}, {@code methods} or {@code tests}
 * @param threads    number of parallel threads (ignored for {@code none})
 * @param retryCount extra attempts for a test that failed for a transient infrastructure reason
 */
public record ExecutionSettings(String parallel, int threads, int retryCount) {

    // TestNG's parallel modes ("none" = serial)
    private static final Set<String> MODES = Set.of("none", "classes", "methods", "tests");

    // Compact constructor: normalises and validates before the fields are set
    public ExecutionSettings {
        parallel = parallel.trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(parallel)) {
            throw new IllegalArgumentException("Unknown parallel mode '" + parallel + "'. Valid values: " + MODES);
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retry.count must be 0 or more: " + retryCount);
        }
    }

    /** False only for {@code none}. */
    public boolean isParallel() {
        return !"none".equals(parallel);
    }
}
