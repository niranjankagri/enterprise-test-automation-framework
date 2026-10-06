package com.enterprise.automation.driver;

import com.enterprise.automation.config.TestConfig;
import java.net.MalformedURLException;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a new, fully configured {@link WebDriver}.
 *
 * <p>The factory only creates; it does not keep the driver. Holding one driver per thread is
 * {@link DriverManager}'s job. That split keeps creation logic (which browser, where) apart from
 * lifecycle logic (who owns it, when it is quit).
 */
public final class DriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    /** A new browser session for {@code config}: local, on a Grid, or (Milestone 9) in the cloud. */
    public static WebDriver create(TestConfig config) {
        MutableCapabilities options = BrowserOptionsFactory.create(config);
        WebDriver driver = switch (config.execution()) {
            case LOCAL -> local(options);
            case REMOTE -> remote(config, options);
            case CLOUD -> throw new UnsupportedOperationException(
                    "Cloud execution is added in Milestone 9; use -Dexecution=local or remote");
        };
        try {
            configure(driver, config);
        } catch (RuntimeException e) {
            driver.quit(); // the session exists already: never leak a browser on a failed setup
            throw e;
        }
        LOG.info("Started {} ({}, {})", config.browser(), config.execution(), config.headless() ? "headless" : "headed");
        return driver;
    }

    /** Local browser. Selenium Manager finds or downloads the matching driver binary. */
    private static WebDriver local(MutableCapabilities options) {
        if (options instanceof ChromeOptions chrome) {
            return new ChromeDriver(chrome);
        }
        if (options instanceof EdgeOptions edge) {
            return new EdgeDriver(edge);
        }
        if (options instanceof FirefoxOptions firefox) {
            return new FirefoxDriver(firefox);
        }
        throw new IllegalArgumentException("Unsupported options type " + options.getClass().getName());
    }

    /** Browser on a Selenium Grid or standalone server at {@code grid.url}. */
    private static WebDriver remote(TestConfig config, MutableCapabilities options) {
        try {
            return new RemoteWebDriver(config.gridUrl().toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid grid.url " + config.gridUrl(), e);
        }
    }

    /**
     * Settings applied to every session, whatever the browser. No implicit wait on purpose:
     * mixing implicit and explicit waits makes timeouts unpredictable, so the framework only
     * uses explicit waits.
     */
    private static void configure(WebDriver driver, TestConfig config) {
        driver.manage().timeouts().pageLoadTimeout(config.pageLoadTimeout());
        driver.manage().window().setSize(new Dimension(config.windowWidth(), config.windowHeight()));
    }
}
