package com.enterprise.automation.ui;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Base of every Component Object: a part of the UI (table, modal, header...) identified by a
 * root locator. Child elements are searched inside the root, so the same component class works
 * wherever the component appears, and two tables on one page never get mixed up.
 */
public abstract class BaseComponent {

    // Same building blocks as a page: browser, waits, actions
    protected final WebDriver driver;
    protected final WaitUtils wait;
    protected final ElementActions actions;
    // Locator of the component's outer element; children are searched inside it
    protected final By root;

    protected BaseComponent(By root) {
        this.driver = DriverManager.getDriver();
        this.wait = new WaitUtils(driver, ConfigManager.config().explicitWait());
        this.actions = new ElementActions(driver, wait);
        this.root = root;
    }

    /** The root element, once visible. */
    protected WebElement rootElement() {
        return wait.visible(root);
    }

    /** A child of the root, waited for. */
    protected WebElement child(By locator) {
        // Root is looked up again on every poll: if the component re-renders, the new one is used
        return wait.until(d -> {
            WebElement element = d.findElement(root).findElement(locator);
            // null = "not yet" for the wait; it keeps polling until visible or timeout
            return element.isDisplayed() ? element : null;
        }, locator + " inside " + root);
    }

    /** Immediate check, no waiting: is the component on screen right now? */
    public boolean isDisplayed() {
        return actions.isDisplayed(root);
    }
}
