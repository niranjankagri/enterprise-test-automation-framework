package com.enterprise.automation.listeners;

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

    private static final List<Class<? extends Throwable>> TRANSIENT_TYPES = List.of(
            SessionNotCreatedException.class,
            NoSuchSessionException.class,
            UnreachableBrowserException.class,
            ConnectException.class,
            SocketTimeoutException.class,
            HttpTimeoutException.class);

    private static final List<String> TRANSIENT_MESSAGES = List.of(
            "chrome not reachable",
            "browser has disconnected",
            "connection refused",
            "connection reset",
            "could not start a new session");

    private TransientFailures() {
    }

    /** True if {@code failure}, or any of its causes, is a known infrastructure problem. */
    public static boolean isTransient(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof AssertionError) {
                return false; // a failed check is never infrastructure
            }
            for (Class<? extends Throwable> type : TRANSIENT_TYPES) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
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
