package com.enterprise.automation.ui;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.utils.WaitUtils;
import org.openqa.selenium.WebDriver;

/**
 * Technical base of every page: driver, waits, actions and navigation. No business logic and no
 * locators of any specific page live here.
 *
 * <p>The driver comes from {@link DriverManager} (this thread's browser), so page objects are
 * thread-safe without passing drivers around.
 */
public abstract class BasePage {

    // This thread's browser
    protected final WebDriver driver;
    // Explicit waits with the configured timeout
    protected final WaitUtils wait;
    // Clicks, typing, reading: each waits first and becomes a report step
    protected final ElementActions actions;

    protected BasePage() {
        // Pages are created freely in tests (new CustomerPage()); they find the browser themselves
        this.driver = DriverManager.getDriver();
        this.wait = new WaitUtils(driver, ConfigManager.config().explicitWait());
        this.actions = new ElementActions(driver, wait);
    }

    /** Opens {@code path} relative to the configured base URL, e.g. {@code /login.html}. */
    protected void navigateTo(String path) {
        // The base URL comes from the environment, so the same page works on local, qa and staging
        String url = ConfigManager.config().baseUrl().resolve(path).toString();
        Report.step("Open " + url, () -> driver.get(url));
    }

    /** The browser tab's title. */
    public String title() {
        return driver.getTitle();
    }

    /** The URL the browser is on now (e.g. to check a redirect). */
    public String currentUrl() {
        return driver.getCurrentUrl();
    }
}
