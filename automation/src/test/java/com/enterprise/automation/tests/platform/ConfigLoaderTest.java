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

    /** Loads a configuration from the given "system properties" and "environment variables" only. */
    private static TestConfig load(Map<String, String> systemProperties, Map<String, String> environmentVariables) {
        return new ConfigLoader(systemProperties, environmentVariables).load();
    }

    /** Without any setting the local environment and its defaults are used. */
    public void localIsTheDefaultEnvironment() {
        TestConfig config = load(Map.of(), Map.of());

        assertThat(config.environment()).isEqualTo("local");
        assertThat(config.baseUrl()).hasToString("http://localhost:8080");
        assertThat(config.browser()).isEqualTo(BrowserType.CHROME);
        assertThat(config.headless()).as("default.properties sets headless=false").isFalse();
        assertThat(config.execution()).isEqualTo(ExecutionMode.LOCAL);
        assertThat(config.explicitWait()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.appAutostart()).as("local starts the demo app").isTrue();
        assertThat(config.database()).hasValueSatisfying(db -> {
            assertThat(db.url()).isEqualTo("jdbc:h2:tcp://localhost:9092/mem:shop");
            assertThat(db.password()).as("H2 demo database has an empty password").isEmpty();
        });
    }

    /** -Denv=qa loads the qa file; keys it does not set fall back to the defaults. */
    public void envSystemPropertySwitchesTheEnvironmentFile() {
        TestConfig qa = load(Map.of("env", "qa"), Map.of());

        assertThat(qa.environment()).isEqualTo("qa");
        assertThat(qa.baseUrl()).hasToString("http://localhost:8081");
        assertThat(qa.headless()).as("qa runs headless").isTrue();
        assertThat(qa.appAutostart()).as("qa inherits app.autostart=false from defaults").isFalse();
        assertThat(qa.explicitWait()).as("qa overrides the default wait").isEqualTo(Duration.ofSeconds(15));
        assertThat(qa.windowWidth()).as("not set in qa: falls back to default").isEqualTo(1440);
    }

    /** ENV=staging loads the staging file, and secrets come from environment variables. */
    public void envEnvironmentVariableSwitchesTheEnvironmentFile() {
        TestConfig staging = load(Map.of(), Map.of("ENV", "staging",
                "ADMIN_PASSWORD", "from-ci-secret", "VIEWER_PASSWORD", "from-ci-secret"));

        assertThat(staging.environment()).isEqualTo("staging");
        assertThat(staging.execution()).isEqualTo(ExecutionMode.REMOTE);
        assertThat(staging.admin().password()).isEqualTo("from-ci-secret");
        assertThat(staging.database()).as("no database access unless a pipeline provides DB_URL").isEmpty();
    }

    /** An environment without its secrets stops the run and names the variable to set. */
    public void secretsMissingFromAnEnvironmentFailFast() {
        // Staging commits no passwords: without the secrets the run stops and says which variable to set
        assertThatThrownBy(() -> load(Map.of("env", "staging"), Map.of()))
                .hasMessageContaining("admin.password")
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    /** Neither the credentials nor the whole configuration print a password. */
    public void credentialsNeverPrintTheirPassword() {
        TestConfig config = load(Map.of(), Map.of());

        assertThat(config.admin().toString()).contains("admin").doesNotContain(config.admin().password());
        assertThat(config.toString()).doesNotContain(config.admin().password());
    }

    /** The precedence: system property over environment variable over file. */
    public void systemPropertyBeatsEnvironmentVariableBeatsFile() {
        // Environment variable over the file, then system property over the environment variable
        TestConfig fromVariable = load(Map.of(), Map.of("BROWSER", "edge"));
        TestConfig fromProperty = load(Map.of("browser", "firefox"), Map.of("BROWSER", "edge"));

        assertThat(fromVariable.browser()).isEqualTo(BrowserType.EDGE);
        assertThat(fromProperty.browser()).isEqualTo(BrowserType.FIREFOX);
    }

    /** Headless mode and both base URLs can be overridden. */
    public void headlessAndUrlsCanBeOverridden() {
        TestConfig config = load(Map.of("headless", "true", "base.url", "http://shop.test:9000"),
                Map.of("API_BASE_URL", "http://shop.test:9000/api"));

        assertThat(config.headless()).isTrue();
        assertThat(config.baseUrl()).hasToString("http://shop.test:9000");
        assertThat(config.apiBaseUrl()).hasToString("http://shop.test:9000/api");
    }

    /** Boolean values ignore surrounding whitespace and letter case. */
    public void booleanValuesAreTrimmedAndCaseInsensitive() {
        TestConfig config = load(Map.of("headless", "  TrUe  "), Map.of("APP_AUTOSTART", " fAlSe "));

        assertThat(config.headless()).isTrue();
        assertThat(config.appAutostart()).isFalse();
    }

    /** Boolean settings follow system property, environment variable, then environment-file precedence. */
    public void booleanSettingsFollowConfigurationPrecedence() {
        TestConfig fromFile = load(Map.of(), Map.of());
        TestConfig fromEnvironment = load(Map.of(), Map.of("HEADLESS", "true", "APP_AUTOSTART", "false"));
        TestConfig fromSystemProperty = load(Map.of("headless", "false", "app.autostart", "true"),
                Map.of("HEADLESS", "true", "APP_AUTOSTART", "false"));

        assertThat(fromFile.headless()).isFalse();
        assertThat(fromFile.appAutostart()).isTrue();
        assertThat(fromEnvironment.headless()).isTrue();
        assertThat(fromEnvironment.appAutostart()).isFalse();
        assertThat(fromSystemProperty.headless()).isFalse();
        assertThat(fromSystemProperty.appAutostart()).isTrue();
    }

    /** An invalid headless value fails with a key-specific error that does not echo the supplied value. */
    public void invalidHeadlessValueFailsClearlyWithoutEchoingIt() {
        String headlessValue = "headless-synthetic-secret";

        assertThatThrownBy(() -> load(Map.of("headless", headlessValue), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).contains("headless").contains("true or false")
                            .doesNotContain(headlessValue);
                });
    }

    /** An invalid autostart value fails with a key-specific error that does not echo the supplied value. */
    public void invalidAutostartValueFailsClearlyWithoutEchoingIt() {
        String autostartValue = "autostart-synthetic-secret";

        assertThatThrownBy(() -> load(Map.of("app.autostart", autostartValue), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).contains("app.autostart").contains("true or false")
                            .doesNotContain(autostartValue);
                });
    }

    /** Retry count accepts zero and positive values, but rejects negative values. */
    public void retryCountValidatesItsNonNegativeBoundary() {
        assertThat(load(Map.of("retry.count", "0"), Map.of()).runSettings().retryCount()).isZero();
        assertThat(load(Map.of("retry.count", "3"), Map.of()).runSettings().retryCount()).isEqualTo(3);

        assertThatThrownBy(() -> load(Map.of("retry.count", "-1"), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retry.count")
                .hasMessageContaining("0 or more")
                .hasMessageContaining("-1");
    }

    /** An unknown environment stops the run with the list of valid ones. */
    public void unknownEnvironmentFailsWithAHint() {
        // "prod" has no file: a typo must stop the run, not silently use defaults
        assertThatThrownBy(() -> load(Map.of("env", "prod"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown environment")
                .hasMessageContaining("-Denv=local|qa|staging");
    }

    /** An unknown browser stops the run with the list of valid ones. */
    public void unknownBrowserListsTheValidOnes() {
        assertThatThrownBy(() -> load(Map.of("browser", "safarii"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safarii")
                .hasMessageContaining("chrome, firefox, edge");
    }

    /** An invalid URL or a non-positive timeout stops the run, naming the key. */
    public void invalidValuesFailFast() {
        assertThatThrownBy(() -> load(Map.of("base.url", "not a url"), Map.of()))
                .hasMessageContaining("base.url");
        assertThatThrownBy(() -> load(Map.of("timeout.explicit.seconds", "0"), Map.of()))
                .hasMessageContaining("positive");
    }

    /** Invalid URL diagnostics identify the key without retaining credentials or query secrets. */
    public void invalidUrlDiagnosticDoesNotExposeCredentialsOrQueryValues() {
        String suppliedUrl = "http://synthetic-user:synthetic-pass@shop.test/api?token=synthetic-query%ZZ";

        assertThatThrownBy(() -> load(Map.of("base.url", suppliedUrl), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    StringBuilder details = new StringBuilder();
                    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                        if (cause.getMessage() != null) {
                            details.append(cause.getMessage()).append(' ');
                        }
                    }
                    assertThat(details.toString()).contains("base.url").contains("not a valid URL")
                            .doesNotContain("synthetic-user", "synthetic-pass", "synthetic-query");
                });
    }
}
