package com.enterprise.automation.tests.base;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Base class of every UI test: one fresh browser per test method, a screenshot when the test
 * fails, and the browser always quit afterwards.
 *
 * <p>A fresh browser per test keeps tests independent: no cookies, session storage or open
 * dialogs leak from one test into the next, and tests can run in any order or in parallel.
 */
public abstract class BaseTest {

    // alwaysRun: also runs when a suite selects tests by group (otherwise TestNG would skip it)
    @BeforeMethod(alwaysRun = true)
    public void startBrowser() {
        DriverManager.startDriver();
    }

    /**
     * After every test: the registered clean-ups (they may still need the browser), then quit the
     * browser. Failure evidence (screenshot, URL, page source) was already taken by
     * {@code ReportEvidenceListener} right after the test method.
     */
    @AfterMethod(alwaysRun = true)
    public void stopBrowser() {
        try {
            CleanupRegistry.runAll();
        } finally {
            // finally: the browser is quit even if a clean-up throws something unexpected
            DriverManager.quitDriver();
        }
    }

    /** The run configuration (environment, accounts, ...). */
    protected TestConfig config() {
        return ConfigManager.config();
    }

    /** Opens the login page in this test's browser. */
    protected LoginPage openLoginPage() {
        return new LoginPage().open();
    }

    /** Signs in as the configured admin; returns the loaded dashboard. */
    protected DashboardPage loginAsAdmin() {
        return openLoginPage().loginAs(config().admin());
    }

    /** Signs in as the configured read-only viewer. */
    protected DashboardPage loginAsViewer() {
        return openLoginPage().loginAs(config().viewer());
    }
}
