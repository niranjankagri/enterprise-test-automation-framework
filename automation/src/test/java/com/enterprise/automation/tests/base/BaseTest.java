package com.enterprise.automation.tests.base;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import com.enterprise.automation.utils.ScreenshotUtils;
import org.testng.ITestResult;
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

    @BeforeMethod(alwaysRun = true)
    public void startBrowser() {
        DriverManager.startDriver();
    }

    @AfterMethod(alwaysRun = true)
    public void stopBrowser(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE && DriverManager.hasDriver()) {
                ScreenshotUtils.save(DriverManager.getDriver(),
                        result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName());
            }
        } finally {
            DriverManager.quitDriver();
        }
    }

    protected TestConfig config() {
        return ConfigManager.config();
    }

    protected LoginPage openLoginPage() {
        return new LoginPage().open();
    }

    protected DashboardPage loginAsAdmin() {
        return openLoginPage().loginAs(config().admin());
    }

    protected DashboardPage loginAsViewer() {
        return openLoginPage().loginAs(config().viewer());
    }
}
