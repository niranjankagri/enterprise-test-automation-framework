package com.enterprise.automation.utils;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Explicit waits: the only way the framework waits. There is no {@code Thread.sleep} and no
 * implicit wait anywhere.
 *
 * <p>Every wait ignores {@link StaleElementReferenceException} and {@link NoSuchElementException},
 * because modern UIs re-render elements while data loads; the condition is simply checked again
 * on the next poll. ChromeDriver's generic "node does not belong to the document" error is the
 * same race in other words and is treated the same way.
 */
public final class WaitUtils {

    // How often the condition is checked: fast enough to react quickly, slow enough not to flood the driver
    private static final Duration POLLING = Duration.ofMillis(100);

    private final WebDriver driver;
    // Maximum wait (timeout.explicit.seconds), after which a TimeoutException names the condition
    private final Duration timeout;

    public WaitUtils(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.timeout = timeout;
    }

    /** Waits until {@code condition} returns a non-null, non-false value, and returns it. */
    public <T> T until(Function<WebDriver, T> condition, String description) {
        // A new wait per call: WebDriverWait is cheap and not meant to be shared between threads
        WebDriverWait wait = new WebDriverWait(driver, timeout, POLLING);
        // Elements re-rendered or not yet created mean "try again", not "fail"
        wait.ignoring(StaleElementReferenceException.class).ignoring(NoSuchElementException.class);
        // The description becomes the timeout message: "Expected condition failed: <description>"
        wait.withMessage(description);
        return wait.until(d -> {
            try {
                return condition.apply(d);
            } catch (WebDriverException e) {
                // ChromeDriver reports some stale-node races (an element replaced while the page
                // navigates) as a generic error instead of StaleElementReferenceException: same handling
                if (isStaleNodeRace(e)) {
                    return null;
                }
                throw e;
            }
        });
    }

    /** "Node with given id does not belong to the document": a stale element in ChromeDriver's words. */
    static boolean isStaleNodeRace(WebDriverException e) {
        return e.getMessage() != null && e.getMessage().contains("does not belong to the document");
    }

    // The helpers below wrap Selenium's ExpectedConditions with a readable description each

    /** Present in the DOM and visible. */
    public WebElement visible(By locator) {
        return until(ExpectedConditions.visibilityOfElementLocated(locator)::apply, "visible: " + locator);
    }

    /** Every matching element visible (at least one must exist). */
    public List<WebElement> allVisible(By locator) {
        return until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator)::apply, "all visible: " + locator);
    }

    /** Visible and enabled. */
    public WebElement clickable(By locator) {
        return until(ExpectedConditions.elementToBeClickable(locator)::apply, "clickable: " + locator);
    }

    /** In the DOM, visible or not. */
    public WebElement present(By locator) {
        return until(ExpectedConditions.presenceOfElementLocated(locator)::apply, "present: " + locator);
    }

    /** Hidden or gone (e.g. the loading bar, a closed dialog). */
    public void invisible(By locator) {
        until(ExpectedConditions.invisibilityOfElementLocated(locator)::apply, "invisible: " + locator);
    }

    /** The element's text contains {@code text}. */
    public void textContains(By locator, String text) {
        until(ExpectedConditions.textToBePresentInElementLocated(locator, text)::apply,
                "text '" + text + "' in " + locator);
    }

    /** An attribute has exactly this value, e.g. body[data-ready="true"]. */
    public void attributeIs(By locator, String attribute, String value) {
        until(ExpectedConditions.attributeToBe(locator, attribute, value)::apply,
                attribute + "='" + value + "' on " + locator);
    }

    /** The browser URL contains {@code fragment} (navigation finished). */
    public void urlContains(String fragment) {
        until(ExpectedConditions.urlContains(fragment)::apply, "URL containing '" + fragment + "'");
    }
}
