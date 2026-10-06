package com.enterprise.automation.driver;

import com.enterprise.automation.config.ConfigManager;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the browser of the current thread.
 *
 * <p>Each test thread gets its own {@link WebDriver} through a {@link ThreadLocal}, so tests can
 * run in parallel without sharing (and corrupting) a browser. Page objects ask for
 * {@link #getDriver()} instead of receiving a static driver.
 *
 * <p>Lifecycle: {@link #startDriver()} in a {@code @BeforeMethod}, {@link #quitDriver()} in an
 * {@code @AfterMethod(alwaysRun = true)}.
 */
public final class DriverManager {

    private static final Logger LOG = LoggerFactory.getLogger(DriverManager.class);
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    /**
     * Starts a browser for this thread from the run configuration. A browser left over from an
     * earlier test on the same thread is quit first, so nothing leaks.
     */
    public static WebDriver startDriver() {
        if (hasDriver()) {
            LOG.warn("A browser was still open on thread {}; quitting it before starting a new one",
                    Thread.currentThread().getName());
            quitDriver();
        }
        WebDriver driver = DriverFactory.create(ConfigManager.config());
        DRIVER.set(driver);
        return driver;
    }

    /** The browser of this thread. */
    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("No browser on thread " + Thread.currentThread().getName()
                    + ". Call DriverManager.startDriver() first (BaseTest does this for UI tests).");
        }
        return driver;
    }

    /** Whether this thread currently owns a browser. */
    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    /**
     * Quits this thread's browser, if any. The ThreadLocal is cleared even if {@code quit()}
     * fails (e.g. the browser already crashed): pooled threads must never keep a dead driver.
     */
    public static void quitDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            LOG.warn("Browser did not quit cleanly: {}", e.getMessage());
        } finally {
            DRIVER.remove();
        }
    }
}
