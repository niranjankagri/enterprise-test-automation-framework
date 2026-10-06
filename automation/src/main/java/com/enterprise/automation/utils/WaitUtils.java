package com.enterprise.automation.utils;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Explicit waits: the only way the framework waits. There is no {@code Thread.sleep} and no
 * implicit wait anywhere.
 *
 * <p>Every wait ignores {@link StaleElementReferenceException} and {@link NoSuchElementException},
 * because modern UIs re-render elements while data loads; the condition is simply checked again
 * on the next poll.
 */
public final class WaitUtils {

    private static final Duration POLLING = Duration.ofMillis(100);

    private final WebDriver driver;
    private final Duration timeout;

    public WaitUtils(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.timeout = timeout;
    }

    /** Waits until {@code condition} returns a non-null, non-false value, and returns it. */
    public <T> T until(Function<WebDriver, T> condition, String description) {
        WebDriverWait wait = new WebDriverWait(driver, timeout, POLLING);
        wait.ignoring(StaleElementReferenceException.class).ignoring(NoSuchElementException.class);
        wait.withMessage(description);
        return wait.until(condition::apply);
    }

    public WebElement visible(By locator) {
        return until(ExpectedConditions.visibilityOfElementLocated(locator)::apply, "visible: " + locator);
    }

    public List<WebElement> allVisible(By locator) {
        return until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator)::apply, "all visible: " + locator);
    }

    public WebElement clickable(By locator) {
        return until(ExpectedConditions.elementToBeClickable(locator)::apply, "clickable: " + locator);
    }

    public WebElement present(By locator) {
        return until(ExpectedConditions.presenceOfElementLocated(locator)::apply, "present: " + locator);
    }

    public void invisible(By locator) {
        until(ExpectedConditions.invisibilityOfElementLocated(locator)::apply, "invisible: " + locator);
    }

    public void textContains(By locator, String text) {
        until(ExpectedConditions.textToBePresentInElementLocated(locator, text)::apply,
                "text '" + text + "' in " + locator);
    }

    public void attributeIs(By locator, String attribute, String value) {
        until(ExpectedConditions.attributeToBe(locator, attribute, value)::apply,
                attribute + "='" + value + "' on " + locator);
    }

    public void urlContains(String fragment) {
        until(ExpectedConditions.urlContains(fragment)::apply, "URL containing '" + fragment + "'");
    }
}
