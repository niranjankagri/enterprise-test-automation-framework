package com.enterprise.automation.driver;

import com.enterprise.automation.config.TestConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Builds the browser options (capabilities) for the configured browser.
 *
 * <p>Kept separate from {@link DriverFactory} because the same options are used for every
 * execution mode: a local ChromeDriver and a Chrome node on a Grid get identical settings,
 * so "works locally, fails on the Grid" cannot come from different flags.
 */
public final class BrowserOptionsFactory {

    // Static factory only
    private BrowserOptionsFactory() {
    }

    /** Options for {@code config.browser()}, with headless mode and page-load strategy applied. */
    public static MutableCapabilities create(TestConfig config) {
        // Exhaustive switch over the enum: a new browser type will not compile until it is handled here
        return switch (config.browser()) {
            case CHROME -> chrome(config);
            case FIREFOX -> firefox(config);
            case EDGE -> edge(config);
        };
    }

    private static ChromeOptions chrome(TestConfig config) {
        ChromeOptions options = new ChromeOptions();
        // NORMAL: navigation returns when the page has fully loaded (document "complete")
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.addArguments(chromiumArguments(config));
        // Browser preferences: no password manager, no leak-detection dialog
        options.setExperimentalOption("prefs", NO_PASSWORD_MANAGER);
        return options;
    }

    private static EdgeOptions edge(TestConfig config) {
        // Same settings as Chrome: Edge is Chromium too
        EdgeOptions options = new EdgeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.addArguments(chromiumArguments(config));
        options.setExperimentalOption("prefs", NO_PASSWORD_MANAGER);
        return options;
    }

    /**
     * Test browsers must not save or check passwords. Chrome's leak detection compares a password
     * typed into a login form with known breaches and then opens a native "change your password"
     * dialog. That dialog is not part of the page (screenshots do not show it) but swallows every
     * click until it is closed. It made viewer tests fail on Chrome only.
     */
    private static final Map<String, Object> NO_PASSWORD_MANAGER = Map.of(
            "credentials_enable_service", false,
            "profile.password_manager_enabled", false,
            "profile.password_manager_leak_detection", false);

    private static FirefoxOptions firefox(TestConfig config) {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        // Firefox's own headless flag (single dash)
        if (config.headless()) {
            options.addArguments("-headless");
        }
        return options;
    }

    /** Chrome and Edge share the Chromium engine, so they share their arguments. */
    private static String[] chromiumArguments(TestConfig config) {
        // Always: no first-run "choose your search engine" screen, no password leak check
        List<String> args = new ArrayList<>(List.of("--disable-search-engine-choice-screen",
                "--disable-features=PasswordLeakDetection"));
        if (config.headless()) {
            // New headless mode (same engine as headed); /dev/shm is tiny in containers, use /tmp instead
            args.addAll(List.of("--headless=new", "--disable-dev-shm-usage"));
        }
        // CI runners and containers often cannot start Chromium's sandbox; Edge then exits at start-up
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            args.add("--no-sandbox");
        }
        return args.toArray(String[]::new);
    }
}
