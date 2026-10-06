package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.reporting.LogComponent;
import org.testng.annotations.Test;

/** Every log line names its layer, so one layer can be followed through a test. */
@Test(groups = {"unit"})
public class LogComponentTest {

    public void frameworkPackagesMapToTheirLayer() {
        assertThat(LogComponent.of("com.enterprise.automation.api.ApiLoggingFilter")).isEqualTo("API");
        assertThat(LogComponent.of("com.enterprise.automation.db.QueryExecutor")).isEqualTo("DB");
        assertThat(LogComponent.of("com.enterprise.automation.ui.ElementActions")).isEqualTo("UI");
        assertThat(LogComponent.of("com.enterprise.automation.driver.DriverManager")).isEqualTo("DRIVER");
        assertThat(LogComponent.of("com.enterprise.automation.listeners.RetryAnalyzer")).isEqualTo("TEST");
        assertThat(LogComponent.of("com.enterprise.automation.utils.ScreenshotUtils")).isEqualTo("UI");
    }

    public void applicationAndLibrariesAreNamedAsSuch() {
        assertThat(LogComponent.of("com.enterprise.demoapp.http.Router")).isEqualTo("APP");
        assertThat(LogComponent.of("org.openqa.selenium.remote.RemoteWebDriver")).isEqualTo("LIB");
        assertThat(LogComponent.of("com.enterprise.automation.FrameworkInfo")).isEqualTo("CORE");
    }
}
