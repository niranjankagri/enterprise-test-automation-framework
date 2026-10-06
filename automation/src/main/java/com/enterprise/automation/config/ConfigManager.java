package com.enterprise.automation.config;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point to the configuration of the current run.
 *
 * <p>The configuration is resolved once, on first use, from the real system properties and
 * environment variables, then shared read-only by every thread. Use
 * {@code ConfigManager.config().baseUrl()} and friends; never read {@code System.getProperty}
 * elsewhere in the framework.
 */
public final class ConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigManager.class);

    // Static access only
    private ConfigManager() {
    }

    /** The configuration of this run (resolved lazily and thread-safely on first call). */
    public static TestConfig config() {
        return Holder.CONFIG;
    }

    /** Initialization-on-demand holder: lazy, thread-safe, no locking on every call. */
    private static final class Holder {
        // The JVM initialises this class (and so loads the configuration) on first access, exactly once
        private static final TestConfig CONFIG = loadFromSystem();
    }

    private static TestConfig loadFromSystem() {
        // System.getProperties() is a Properties (Object keys/values): copy it into a String map
        Map<String, String> systemProperties = new HashMap<>();
        System.getProperties().forEach((k, v) -> systemProperties.put(String.valueOf(k), String.valueOf(v)));

        // The pure loader does the work; this class only feeds it the real JVM sources
        TestConfig config = new ConfigLoader(systemProperties, System.getenv()).load();
        // One line in every run's log: which environment, URL, browser and mode (no secrets)
        LOG.info("Configuration: {}", config.summary());
        return config;
    }
}
