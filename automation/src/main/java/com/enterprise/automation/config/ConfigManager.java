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

    private ConfigManager() {
    }

    /** The configuration of this run (resolved lazily and thread-safely on first call). */
    public static TestConfig config() {
        return Holder.CONFIG;
    }

    /** Initialization-on-demand holder: lazy, thread-safe, no locking on every call. */
    private static final class Holder {
        private static final TestConfig CONFIG = loadFromSystem();
    }

    private static TestConfig loadFromSystem() {
        Map<String, String> systemProperties = new HashMap<>();
        System.getProperties().forEach((k, v) -> systemProperties.put(String.valueOf(k), String.valueOf(v)));

        TestConfig config = new ConfigLoader(systemProperties, System.getenv()).load();
        LOG.info("Configuration: {}", config.summary());
        return config;
    }
}
