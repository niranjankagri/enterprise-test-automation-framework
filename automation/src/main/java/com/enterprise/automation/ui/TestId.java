package com.enterprise.automation.ui;

import org.openqa.selenium.By;

/**
 * Locator strategy for the application's {@code data-testid} attributes.
 *
 * <p>Test ids are a contract between the application and its tests: they do not change when
 * layout, styling or text changes, which makes them the most stable locator there is.
 */
public final class TestId {

    private TestId() {
    }

    /** {@code [data-testid='value']} */
    public static By of(String value) {
        return By.cssSelector("[data-testid='" + value + "']");
    }
}
