package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.automation.driver.DriverManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Checks the driver platform with a real browser: the configured browser starts with the
 * configured settings, each thread gets its own session, and quitting cleans up.
 *
 * <p>Uses a {@code data:} page, so it needs no application or network.
 */
@Test(groups = "platform")
public class DriverManagerTest {

    private static final String PAGE = "data:text/html,<title>Driver check</title><h1>ok</h1>";

    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    @AfterMethod(alwaysRun = true)
    public void quitBrowser() {
        DriverManager.quitDriver();
    }

    public void configuredBrowserStartsWithConfiguredSettings() {
        TestConfig config = ConfigManager.config();
        WebDriver driver = DriverManager.startDriver();

        driver.get(PAGE);

        assertThat(driver.getTitle()).isEqualTo("Driver check");
        String expectedName = switch (config.browser()) {
            case CHROME -> "chrome";
            case FIREFOX -> "firefox";
            case EDGE -> "MicrosoftEdge";
        };
        assertThat(((RemoteWebDriver) driver).getCapabilities().getBrowserName())
                .as("the configured browser actually started").isEqualTo(expectedName);
        Dimension size = driver.manage().window().getSize();
        if (config.headless()) {
            assertThat(size).isEqualTo(new Dimension(config.windowWidth(), config.windowHeight()));
        } else {
            // A visible window is clamped by the operating system to the screen it is on, and display
            // scaling (e.g. 125 %) rounds the size by a few pixels
            int rounding = 10;
            assertThat(size.getWidth()).isPositive().isLessThanOrEqualTo(config.windowWidth() + rounding);
            assertThat(size.getHeight()).isPositive().isLessThanOrEqualTo(config.windowHeight() + rounding);
        }
    }

    /** Four sessions over two threads: every session is unique and stays with its own thread. */
    @Test(invocationCount = 4, threadPoolSize = 2)
    public void everyThreadGetsItsOwnBrowser() {
        WebDriver driver = DriverManager.startDriver();
        String sessionId = ((RemoteWebDriver) driver).getSessionId().toString();

        driver.get(PAGE);

        assertThat(sessionIds.add(sessionId)).as("session %s is not shared with another test", sessionId).isTrue();
        assertThat(DriverManager.getDriver()).as("same thread, same browser").isSameAs(driver);
    }

    public void quitReleasesTheBrowserOfThisThread() {
        DriverManager.startDriver();
        assertThat(DriverManager.hasDriver()).isTrue();

        DriverManager.quitDriver();

        assertThat(DriverManager.hasDriver()).isFalse();
        assertThatThrownBy(DriverManager::getDriver)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startDriver");
    }
}
