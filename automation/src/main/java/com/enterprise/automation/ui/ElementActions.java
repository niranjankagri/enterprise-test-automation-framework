package com.enterprise.automation.ui;

import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.utils.WaitUtils;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User actions on elements, each preceded by the right wait (visible to read, clickable to click).
 *
 * <p>Pages and components get one of these instead of calling WebDriver directly, so no page can
 * "forget" to wait, and every action is logged at DEBUG level for failure analysis.
 */
public final class ElementActions {

    private static final Logger LOG = LoggerFactory.getLogger(ElementActions.class);

    private final WebDriver driver;
    private final WaitUtils wait;

    public ElementActions(WebDriver driver, WaitUtils wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void click(By locator) {
        LOG.debug("click {}", locator);
        Report.step("Click " + describe(locator), () -> {
            // Inside the wait: if the element re-renders between "clickable" and "click", try again
            wait.until(d -> {
                wait.clickable(locator).click();
                return true;
            }, "click " + locator);
        });
    }

    public void type(By locator, String text) {
        LOG.debug("type into {}", locator);
        String shown = describe(locator).toLowerCase(java.util.Locale.ROOT).contains("password") ? "****" : text;
        Report.step("Type '" + shown + "' into " + describe(locator), () -> {
            WebElement element = wait.visible(locator);
            element.clear();
            element.sendKeys(text);
        });
    }

    /** "customer-search" for a test id locator, the locator itself otherwise: short step names. */
    private static String describe(By locator) {
        String text = locator.toString();
        int start = text.indexOf("data-testid='");
        return start < 0 ? text : text.substring(start + 13, text.indexOf('\'', start + 13));
    }

    public String text(By locator) {
        return wait.visible(locator).getText().trim();
    }

    public String value(By locator) {
        return wait.visible(locator).getDomProperty("value");
    }

    public void selectByVisibleText(By locator, String text) {
        LOG.debug("select '{}' in {}", text, locator);
        Report.step("Select '" + text + "' in " + describe(locator),
                () -> new Select(wait.visible(locator)).selectByVisibleText(text));
    }

    /** Selects the first option whose text contains {@code fragment}. */
    public void selectContaining(By locator, String fragment) {
        LOG.debug("select option containing '{}' in {}", fragment, locator);
        Select select = new Select(wait.visible(locator));
        WebElement option = wait.until(d -> select.getOptions().stream()
                .filter(o -> o.getText().contains(fragment)).findFirst().orElse(null),
                "option containing '" + fragment + "' in " + locator);
        select.selectByVisibleText(option.getText());
    }

    /** Immediate check, no waiting: is the element there and visible right now? */
    public boolean isDisplayed(By locator) {
        return isDisplayed(driver, locator);
    }

    public static boolean isDisplayed(SearchContext context, By locator) {
        List<WebElement> elements = context.findElements(locator);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    public boolean isEnabled(By locator) {
        return wait.visible(locator).isEnabled();
    }
}
