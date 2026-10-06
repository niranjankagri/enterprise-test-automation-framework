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

    // This thread's browser (given by the page or component)
    private final WebDriver driver;
    // Explicit waits with the configured timeout
    private final WaitUtils wait;

    public ElementActions(WebDriver driver, WaitUtils wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /** Waits until the element is clickable, clicks it; a report step and a DEBUG log line. */
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

    /** Replaces the field's content with {@code text}. */
    public void type(By locator, String text) {
        // The typed value is not logged (it may be a password)
        LOG.debug("type into {}", locator);
        // In the report, values typed into password fields show as ****
        String shown = describe(locator).toLowerCase(java.util.Locale.ROOT).contains("password") ? "****" : text;
        Report.step("Type '" + shown + "' into " + describe(locator), () -> {
            WebElement element = wait.visible(locator);
            // Clear first: typing appends to what is already there
            element.clear();
            element.sendKeys(text);
        });
    }

    /** "customer-search" for a test id locator, the locator itself otherwise: short step names. */
    private static String describe(By locator) {
        // By.toString() looks like: By.cssSelector: [data-testid='customer-search']
        String text = locator.toString();
        int start = text.indexOf("data-testid='");
        // 13 = length of "data-testid='"; cut out the value up to the closing quote
        return start < 0 ? text : text.substring(start + 13, text.indexOf('\'', start + 13));
    }

    /** Visible text of the element, trimmed. */
    public String text(By locator) {
        return wait.visible(locator).getText().trim();
    }

    /** Current value of an input (the live DOM property, not the HTML attribute). */
    public String value(By locator) {
        return wait.visible(locator).getDomProperty("value");
    }

    /** Picks the option with exactly this visible text in a select. */
    public void selectByVisibleText(By locator, String text) {
        LOG.debug("select '{}' in {}", text, locator);
        Report.step("Select '" + text + "' in " + describe(locator),
                () -> new Select(wait.visible(locator)).selectByVisibleText(text));
    }

    /** Selects the first option whose text contains {@code fragment}. */
    public void selectContaining(By locator, String fragment) {
        LOG.debug("select option containing '{}' in {}", fragment, locator);
        Select select = new Select(wait.visible(locator));
        // Options can be filled asynchronously (e.g. the checkout customers): wait until one matches
        WebElement option = wait.until(d -> select.getOptions().stream()
                .filter(o -> o.getText().contains(fragment)).findFirst().orElse(null),
                "option containing '" + fragment + "' in " + locator);
        select.selectByVisibleText(option.getText());
    }

    /** Immediate check, no waiting: is the element there and visible right now? */
    public boolean isDisplayed(By locator) {
        return isDisplayed(driver, locator);
    }

    /** Same check inside any context (the page or a component's root element). */
    public static boolean isDisplayed(SearchContext context, By locator) {
        // findElements (plural) returns an empty list instead of throwing when nothing matches
        List<WebElement> elements = context.findElements(locator);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    /** Whether the (visible) element is enabled, e.g. a button that depends on the form state. */
    public boolean isEnabled(By locator) {
        return wait.visible(locator).isEnabled();
    }
}
