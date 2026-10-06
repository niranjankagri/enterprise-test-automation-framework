package com.enterprise.automation.ui;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.driver.DriverManager;
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

    protected final WebDriver driver;
    protected final WaitUtils wait;
    protected final ElementActions actions;

    protected BasePage() {
        this.driver = DriverManager.getDriver();
        this.wait = new WaitUtils(driver, ConfigManager.config().explicitWait());
        this.actions = new ElementActions(driver, wait);
    }

    /** Opens {@code path} relative to the configured base URL, e.g. {@code /login.html}. */
    protected void navigateTo(String path) {
        driver.get(ConfigManager.config().baseUrl().resolve(path).toString());
    }

    public String title() {
        return driver.getTitle();
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }
}
