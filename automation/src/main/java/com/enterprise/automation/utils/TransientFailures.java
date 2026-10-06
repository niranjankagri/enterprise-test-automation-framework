package com.enterprise.automation.utils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.remote.UnreachableBrowserException;

/**
 * Decides whether a failure was caused by the infrastructure (browser crashed, Grid node gone,
 * connection refused) rather than by the application or the test.
 *
 * <p>Only those failures may be retried. Assertion failures, wait timeouts (the application was
 * too slow or never showed the expected state) and any other exception are real results and are
 * reported, never retried: retrying them would only turn a red build green while hiding a defect
 * or a flaky test that needs fixing.
 */
public final class TransientFailures {

    // Exception types that always mean "the browser/Grid/network failed"
    private static final List<Class<? extends Throwable>> TRANSIENT_TYPES = List.of(
            SessionNotCreatedException.class,
            NoSuchSessionException.class,
            UnreachableBrowserException.class,
            ConnectException.class,
            SocketTimeoutException.class,
            HttpTimeoutException.class);

    // Messages that identify infrastructure problems inside generic WebDriver/IO exceptions (lower case)
    private static final List<String> TRANSIENT_MESSAGES = List.of(
            "chrome not reachable",
            "browser has disconnected",
            "connection refused",
            "connection reset",
            "could not start a new session",
            // Chromium under load at start-up (seen with Edge on CI runners); not a wait timeout
            "timed out receiving message from renderer");

    // Static helpers only
    private TransientFailures() {
    }

    /** True if {@code failure}, or any of its causes, is a known infrastructure problem. */
    public static boolean isTransient(Throwable failure) {
        // Walk the cause chain (the real reason is often wrapped); stop on a self-referencing cause
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof AssertionError) {
                return false; // a failed check is never infrastructure
            }
            // Known infrastructure exception type
            for (Class<? extends Throwable> type : TRANSIENT_TYPES) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            // Messages are only trusted on IO and Selenium exceptions, so an application error that
            // happens to say "connection refused" is not mistaken for infrastructure
            if (t instanceof IOException || t.getClass().getName().startsWith("org.openqa.selenium")) {
                for (String fragment : TRANSIENT_MESSAGES) {
                    if (message.contains(fragment)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
