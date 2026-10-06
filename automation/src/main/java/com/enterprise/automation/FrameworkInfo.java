package com.enterprise.automation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Identity of the framework build: its name and version.
 *
 * <p>The values come from {@code framework.properties}, which Maven filters at build time, so the
 * version always matches the POM. Reports and logs print them, which answers the first question
 * of any failure investigation: "which framework build produced this result?".
 */
public final class FrameworkInfo {

    private static final String RESOURCE = "/framework.properties";
    private static final Properties PROPERTIES = load();

    private FrameworkInfo() {
    }

    /** Human-readable framework name, e.g. "Enterprise Test Automation Framework". */
    public static String name() {
        return PROPERTIES.getProperty("framework.name");
    }

    /** Maven project version, e.g. "1.0.0-SNAPSHOT". */
    public static String version() {
        return PROPERTIES.getProperty("framework.version");
    }

    private static Properties load() {
        try (InputStream in = FrameworkInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the classpath");
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
