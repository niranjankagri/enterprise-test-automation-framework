package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.config.BrowserType;
import com.enterprise.automation.config.ConfigLoader;
import com.enterprise.automation.config.ExecutionMode;
import com.enterprise.automation.config.TestConfig;
import java.time.Duration;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * Unit tests of the configuration precedence rules. No browser, no network: the loader gets its
 * "system properties" and "environment variables" as plain maps.
 */
@Test(groups = "unit")
public class ConfigLoaderTest {

    private static TestConfig load(Map<String, String> systemProperties, Map<String, String> environmentVariables) {
        return new ConfigLoader(systemProperties, environmentVariables).load();
    }

    public void localIsTheDefaultEnvironment() {
        TestConfig config = load(Map.of(), Map.of());

        assertThat(config.environment()).isEqualTo("local");
        assertThat(config.baseUrl()).hasToString("http://localhost:8080");
        assertThat(config.browser()).isEqualTo(BrowserType.CHROME);
        assertThat(config.execution()).isEqualTo(ExecutionMode.LOCAL);
        assertThat(config.explicitWait()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.appAutostart()).as("local starts the demo app").isTrue();
    }

    public void envSystemPropertySwitchesTheEnvironmentFile() {
        TestConfig qa = load(Map.of("env", "qa"), Map.of());

        assertThat(qa.environment()).isEqualTo("qa");
        assertThat(qa.baseUrl()).hasToString("http://localhost:8081");
        assertThat(qa.headless()).as("qa runs headless").isTrue();
        assertThat(qa.explicitWait()).as("qa overrides the default wait").isEqualTo(Duration.ofSeconds(15));
        assertThat(qa.windowWidth()).as("not set in qa: falls back to default").isEqualTo(1440);
    }

    public void envEnvironmentVariableSwitchesTheEnvironmentFile() {
        TestConfig staging = load(Map.of(), Map.of("ENV", "staging",
                "ADMIN_PASSWORD", "from-ci-secret", "VIEWER_PASSWORD", "from-ci-secret"));

        assertThat(staging.environment()).isEqualTo("staging");
        assertThat(staging.execution()).isEqualTo(ExecutionMode.REMOTE);
        assertThat(staging.admin().password()).isEqualTo("from-ci-secret");
    }

    public void secretsMissingFromAnEnvironmentFailFast() {
        assertThatThrownBy(() -> load(Map.of("env", "staging"), Map.of()))
                .hasMessageContaining("admin.password")
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    public void credentialsNeverPrintTheirPassword() {
        TestConfig config = load(Map.of(), Map.of());

        assertThat(config.admin().toString()).contains("admin").doesNotContain(config.admin().password());
        assertThat(config.toString()).doesNotContain(config.admin().password());
    }

    public void systemPropertyBeatsEnvironmentVariableBeatsFile() {
        TestConfig fromVariable = load(Map.of(), Map.of("BROWSER", "edge"));
        TestConfig fromProperty = load(Map.of("browser", "firefox"), Map.of("BROWSER", "edge"));

        assertThat(fromVariable.browser()).isEqualTo(BrowserType.EDGE);
        assertThat(fromProperty.browser()).isEqualTo(BrowserType.FIREFOX);
    }

    public void headlessAndUrlsCanBeOverridden() {
        TestConfig config = load(Map.of("headless", "true", "base.url", "http://shop.test:9000"),
                Map.of("API_BASE_URL", "http://shop.test:9000/api"));

        assertThat(config.headless()).isTrue();
        assertThat(config.baseUrl()).hasToString("http://shop.test:9000");
        assertThat(config.apiBaseUrl()).hasToString("http://shop.test:9000/api");
    }

    public void unknownEnvironmentFailsWithAHint() {
        assertThatThrownBy(() -> load(Map.of("env", "prod"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown environment")
                .hasMessageContaining("-Denv=local|qa|staging");
    }

    public void unknownBrowserListsTheValidOnes() {
        assertThatThrownBy(() -> load(Map.of("browser", "safarii"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safarii")
                .hasMessageContaining("chrome, firefox, edge");
    }

    public void invalidValuesFailFast() {
        assertThatThrownBy(() -> load(Map.of("base.url", "not a url"), Map.of()))
                .hasMessageContaining("base.url");
        assertThatThrownBy(() -> load(Map.of("timeout.explicit.seconds", "0"), Map.of()))
                .hasMessageContaining("positive");
    }
}
