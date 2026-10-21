package com.enterprise.automation.tests.ui;

import com.enterprise.automation.utils.WaitUtils;
import java.time.Duration;
import java.util.Locale;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/** Controlled fetch failures for browser tests; the hook is installed only in the test browser. */
public final class BrowserRequestSimulator {

    private static final String INSTALL_SCRIPT = """
            const [method, path, status, message, networkError, gated, passThrough] = arguments;
            const originalFetch = window.__tafRequestOriginalFetch || window.fetch;
            let releaseRequest;
            const released = new Promise(resolve => { releaseRequest = resolve; });
            window.__tafRequestOriginalFetch = originalFetch;
            window.__tafRequestPending = false;
            window.__tafRequestScenario = { method, path, status, message, networkError, gated, passThrough, released };
            window.__tafReleaseRequest = releaseRequest;
            window.fetch = async function(input, init = {}) {
              const url = typeof input === 'string' ? input : input.url;
              const requestMethod = (init.method || (input && input.method) || 'GET').toUpperCase();
              const scenario = window.__tafRequestScenario;
              if (!scenario || requestMethod !== scenario.method
                  || new URL(url, window.location.href).pathname !== scenario.path) {
                return originalFetch.call(window, input, init);
              }
              window.__tafRequestPending = true;
              if (scenario.gated) await scenario.released;
              window.__tafRequestPending = false;
              window.__tafRequestScenario = null;
              if (scenario.passThrough) return originalFetch.call(window, input, init);
              if (scenario.networkError) throw new TypeError('Synthetic network failure');
              return new Response(JSON.stringify({ message: scenario.message }), {
                status: scenario.status,
                headers: { 'Content-Type': 'application/json' }
              });
            };
            return true;
            """;

    private static final String AWAIT_PENDING_SCRIPT = "return window.__tafRequestPending === true;";
    private static final String RELEASE_SCRIPT = "window.__tafReleaseRequest();";
    private static final String RESTORE_SCRIPT = """
            if (window.__tafRequestOriginalFetch) window.fetch = window.__tafRequestOriginalFetch;
            delete window.__tafRequestOriginalFetch;
            delete window.__tafRequestPending;
            delete window.__tafRequestScenario;
            delete window.__tafReleaseRequest;
            """;

    private BrowserRequestSimulator() {
    }

    public static void gateNetworkFailure(WebDriver driver, String method, String path) {
        install(driver, method, path, 0, "", true, true);
    }

    public static void gateHttpFailure(WebDriver driver, String method, String path, int status, String message) {
        install(driver, method, path, status, message, false, true);
    }

    public static void respondWithHttpFailure(WebDriver driver, String method, String path, int status, String message) {
        install(driver, method, path, status, message, false, false);
    }

    public static void gatePassThrough(WebDriver driver, String method, String path) {
        install(driver, method, path, 0, "", false, true, true);
    }

    private static void install(WebDriver driver, String method, String path, int status, String message,
                                boolean networkError, boolean gated) {
        install(driver, method, path, status, message, networkError, gated, false);
    }

    private static void install(WebDriver driver, String method, String path, int status, String message,
                                boolean networkError, boolean gated, boolean passThrough) {
        ((JavascriptExecutor) driver).executeScript(INSTALL_SCRIPT, method.toUpperCase(Locale.ROOT), path, status,
                message, networkError, gated, passThrough);
    }

    public static void awaitPending(WebDriver driver, Duration timeout) {
        new WaitUtils(driver, timeout).until(
                currentDriver -> Boolean.TRUE.equals(((JavascriptExecutor) currentDriver)
                        .executeScript(AWAIT_PENDING_SCRIPT)),
                "controlled request to reach its gate");
    }

    public static void release(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript(RELEASE_SCRIPT);
    }

    public static void restore(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript(RESTORE_SCRIPT);
    }
}
