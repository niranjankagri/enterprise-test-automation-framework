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

    protected final WebDriver driver;
    protected final WaitUtils wait;
    protected final ElementActions actions;
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
        return wait.until(d -> {
            WebElement element = d.findElement(root).findElement(locator);
            return element.isDisplayed() ? element : null;
        }, locator + " inside " + root);
    }

    public boolean isDisplayed() {
        return actions.isDisplayed(root);
    }
}
