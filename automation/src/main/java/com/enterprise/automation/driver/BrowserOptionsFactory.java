package com.enterprise.automation.driver;

import com.enterprise.automation.config.TestConfig;
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

    private BrowserOptionsFactory() {
    }

    /** Options for {@code config.browser()}, with headless mode and page-load strategy applied. */
    public static MutableCapabilities create(TestConfig config) {
        return switch (config.browser()) {
            case CHROME -> chrome(config);
            case FIREFOX -> firefox(config);
            case EDGE -> edge(config);
        };
    }

    private static ChromeOptions chrome(TestConfig config) {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.addArguments(chromiumArguments(config));
        return options;
    }

    private static EdgeOptions edge(TestConfig config) {
        EdgeOptions options = new EdgeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.addArguments(chromiumArguments(config));
        return options;
    }

    private static FirefoxOptions firefox(TestConfig config) {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        if (config.headless()) {
            options.addArguments("-headless");
        }
        return options;
    }

    /** Chrome and Edge share the Chromium engine, so they share their arguments. */
    private static String[] chromiumArguments(TestConfig config) {
        return config.headless()
                ? new String[] {"--headless=new", "--disable-dev-shm-usage", "--disable-search-engine-choice-screen"}
                : new String[] {"--disable-search-engine-choice-screen"};
    }
}
