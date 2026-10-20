package com.enterprise.automation.reporting;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureExternalKey;
import io.qameta.allure.AttachmentOptions;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.util.ResultsUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The framework's single entry point to the report: steps and attachments. It masks names and textual content at the
 * final Allure output boundary so callers cannot accidentally persist URL credentials or sensitive query values.
 *
 * <p>Steps use Allure's lifecycle API instead of {@code @Step} annotations. This avoids requiring
 * AspectJ weaving and lets the framework mask failure details before Allure stores them.
 */
public final class Report {

    /** Static helpers only: no instances. */
    private Report() {
    }

    /** Runs {@code action} as a report step named {@code name}; a failure marks the step failed. */
    public static void step(String name, Runnable action) {
        // Nested calls create nested steps (e.g. an API call inside a cleanup)
        runStep(name, () -> {
            action.run();
            return null;
        });
    }

    /** Runs {@code action} as a report step and returns its result. */
    public static <T> T step(String name, java.util.function.Supplier<T> action) {
        return runStep(name, action::get);
    }

    /** Runs a lifecycle step while masking exception details before Allure persists them. */
    private static <T> T runStep(String name, StepAction<T> action) {
        AllureLifecycle lifecycle = Allure.getLifecycle();
        AllureExternalKey key = AllureExternalKey.random(Report.class);
        lifecycle.startStep(key, new StepResult().setName(SecretMasker.mask(name)));
        try {
            T result = action.run();
            lifecycle.updateStep(key, step -> step.setStatus(Status.PASSED));
            return result;
        } catch (Throwable failure) {
            Status status = ResultsUtils.getStatus(failure).orElse(Status.BROKEN);
            StatusDetails details = ResultsUtils.getStatusDetails(failure).orElse(null);
            if (details != null) {
                details.setMessage(SecretMasker.mask(details.getMessage()));
                details.setTrace(SecretMasker.mask(details.getTrace()));
                details.setActual(SecretMasker.mask(details.getActual()));
                details.setExpected(SecretMasker.mask(details.getExpected()));
            }
            lifecycle.updateStep(key, step -> step.setStatus(status).setStatusDetails(details));
            return Report.<RuntimeException, T>rethrow(failure);
        } finally {
            // The no-argument overload also pops the current step from Allure's thread context.
            lifecycle.stopStep();
        }
    }

    @FunctionalInterface
    private interface StepAction<T> {
        T run() throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T rethrow(Throwable failure) throws E {
        throw (E) failure;
    }

    /** A step without body, e.g. a checkpoint. */
    public static void log(String name) {
        Allure.step(SecretMasker.mask(name));
    }

    // Attachments go to the current step (or the test when no step is open); null is written as empty

    public static void attachText(String name, String text) {
        Allure.attachment(SecretMasker.mask(name), "text/plain", SecretMasker.mask(text == null ? "" : text),
                AttachmentOptions.withFileExtension(".txt"));
    }

    /** Attaches JSON to the current report step (empty for null). */
    public static void attachJson(String name, String json) {
        Allure.attachment(SecretMasker.mask(name), "application/json", SecretMasker.mask(json == null ? "" : json),
                AttachmentOptions.withFileExtension(".json"));
    }

    /** Attaches HTML to the current report step (empty for null). */
    public static void attachHtml(String name, String html) {
        Allure.attachment(SecretMasker.mask(name), "text/html", new ByteArrayInputStream(
                SecretMasker.mask(html == null ? "" : html).getBytes(StandardCharsets.UTF_8)),
                AttachmentOptions.withFileExtension(".html"));
    }

    /** Attaches a screenshot to the current report step, if one was taken. */
    public static void attachPng(String name, byte[] png) {
        // No screenshot could be taken (browser gone): nothing to attach
        if (png != null) {
            Allure.attachment(SecretMasker.mask(name), "image/png", new ByteArrayInputStream(png),
                    AttachmentOptions.withFileExtension(".png"));
        }
    }
}
