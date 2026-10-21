package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.selenium.AxeBuilder;
import java.util.List;
import org.openqa.selenium.WebDriver;

/** Focused automated checks for the WCAG rule tags used by critical ShopEase journeys. */
final class AccessibilityAssertions {

    private static final List<String> WCAG_TAGS = List.of(
            "wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa", "best-practice");

    private AccessibilityAssertions() {
    }

    /** Fails with axe's violation details for the current page and UI state. */
    static void pageHasNoViolations(WebDriver driver, String pageState) {
        Results results = new AxeBuilder().withTags(WCAG_TAGS).setTimeout(20_000).analyze(driver);
        assertThat(results.getViolations()).as("axe-core violations on " + pageState).isEmpty();
    }
}
