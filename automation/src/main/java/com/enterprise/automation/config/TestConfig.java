package com.enterprise.automation.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The fully resolved, immutable configuration of one test run.
 *
 * <p>Built once by {@link ConfigLoader}; a record, so nothing can change it mid-run and it is
 * safe to share between parallel test threads.
 *
 * @param environment     name of the environment, e.g. {@code local}, {@code qa}
 * @param baseUrl         URL of the web application
 * @param apiBaseUrl      URL of its REST API
 * @param browser         browser to drive
 * @param headless        run the browser without a window
 * @param execution       where the browser runs
 * @param gridUrl         Selenium Grid URL, used when {@code execution} is {@code REMOTE}
 * @param windowWidth     browser window width in pixels
 * @param windowHeight    browser window height in pixels
 * @param explicitWait    default timeout of explicit waits
 * @param pageLoadTimeout maximum time for a page load
 * @param appAutostart    start the demo application in-process before the suite ({@code local} only)
 * @param admin           account with full rights
 * @param viewer          read-only account
 * @param database        direct database access, or empty where the environment does not allow it
 * @param runSettings     parallel mode, threads and retries of this run
 */
public record TestConfig(
        String environment,
        URI baseUrl,
        URI apiBaseUrl,
        BrowserType browser,
        boolean headless,
        ExecutionMode execution,
        URI gridUrl,
        int windowWidth,
        int windowHeight,
        Duration explicitWait,
        Duration pageLoadTimeout,
        boolean appAutostart,
        Credentials admin,
        Credentials viewer,
        Optional<DatabaseConfig> database,
        ExecutionSettings runSettings) {

    /** One line for logs and reports: what this run is pointed at. */
    public String summary() {
        // Deliberately no credentials and no database password
        return "env=" + environment + ", baseUrl=" + baseUrl + ", browser=" + browser.name().toLowerCase(Locale.ROOT)
                + (headless ? " (headless)" : "") + ", execution=" + execution.name().toLowerCase(Locale.ROOT)
                + ", parallel=" + runSettings.parallel() + (runSettings.isParallel() ? " x" + runSettings.threads() : "")
                + ", retries=" + runSettings.retryCount();
    }
}
