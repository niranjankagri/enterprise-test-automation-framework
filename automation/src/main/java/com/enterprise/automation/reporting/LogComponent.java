package com.enterprise.automation.reporting;

import java.util.Map;

/**
 * The part of the framework a log line comes from (UI, API, DB...), derived from the logger name.
 *
 * <p>Printed as {@code [API]} in every log line ({@link ComponentConverter}) and in the per-test log,
 * so a reader can follow one layer through a test without knowing the class names:
 * <pre>
 * 14:02:11.512 INFO  [TestNG-1] [CustomerApiTest.create] [API] POST /api/customers -> 201 in 35 ms [X-Request-Id 6f1c...]
 * 14:02:11.540 DEBUG [TestNG-1] [CustomerApiTest.create] [DB] customer by email -> 1 row(s) in 4 ms
 * </pre>
 */
public final class LogComponent {

    // Framework package (first segment after com.enterprise.automation) -> component
    private static final Map<String, String> PACKAGES = Map.of(
            "api", "API", "db", "DB", "ui", "UI", "driver", "DRIVER", "config", "CONFIG",
            "data", "DATA", "listeners", "TEST", "tests", "TEST", "reporting", "REPORT");
    // Utility classes that serve one layer
    private static final Map<String, String> CLASSES = Map.of(
            "ScreenshotUtils", "UI", "WaitUtils", "UI", "TransientFailures", "TEST");
    private static final String FRAMEWORK = "com.enterprise.automation.";

    private LogComponent() {
    }

    /** The component of {@code loggerName}: a framework layer, APP for the demo application, LIB for libraries. */
    public static String of(String loggerName) {
        if (loggerName.startsWith(FRAMEWORK)) {
            String rest = loggerName.substring(FRAMEWORK.length());
            int dot = rest.indexOf('.');
            String className = loggerName.substring(loggerName.lastIndexOf('.') + 1);
            // A class directly in com.enterprise.automation (no sub-package) is framework core
            String component = dot < 0 ? "CORE" : PACKAGES.getOrDefault(rest.substring(0, dot), CLASSES.get(className));
            return component == null ? "CORE" : component;
        }
        // The application under test runs in the same JVM for env=local
        return loggerName.startsWith("com.enterprise.demoapp") ? "APP" : "LIB";
    }
}
