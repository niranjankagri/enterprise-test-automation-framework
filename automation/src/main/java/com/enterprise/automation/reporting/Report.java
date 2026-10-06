package com.enterprise.automation.reporting;

import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The framework's single entry point to the report: steps and attachments.
 *
 * <p>Steps use Allure's lambda API instead of {@code @Step} annotations. Annotations need the
 * AspectJ weaver as a Java agent, and the weaver cannot run on current JDKs (27), so annotated
 * steps would silently disappear. Lambda steps work on every JDK and need no agent.
 */
public final class Report {

    private Report() {
    }

    /** Runs {@code action} as a report step named {@code name}; a failure marks the step failed. */
    public static void step(String name, Runnable action) {
        // Nested calls create nested steps (e.g. an API call inside a cleanup)
        Allure.step(name, action::run);
    }

    /** Runs {@code action} as a report step and returns its result. */
    public static <T> T step(String name, java.util.function.Supplier<T> action) {
        return Allure.step(name, action::get);
    }

    /** A step without body, e.g. a checkpoint. */
    public static void log(String name) {
        Allure.step(name);
    }

    // Attachments go to the current step (or the test when no step is open); null is written as empty

    public static void attachText(String name, String text) {
        Allure.addAttachment(name, "text/plain", text == null ? "" : text, ".txt");
    }

    public static void attachJson(String name, String json) {
        Allure.addAttachment(name, "application/json", json == null ? "" : json, ".json");
    }

    public static void attachHtml(String name, String html) {
        Allure.addAttachment(name, "text/html", new ByteArrayInputStream(
                (html == null ? "" : html).getBytes(StandardCharsets.UTF_8)), ".html");
    }

    public static void attachPng(String name, byte[] png) {
        // No screenshot could be taken (browser gone): nothing to attach
        if (png != null) {
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), ".png");
        }
    }
}
