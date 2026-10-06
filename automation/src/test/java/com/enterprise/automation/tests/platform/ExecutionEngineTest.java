package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.config.ConfigLoader;
import com.enterprise.automation.config.ExecutionSettings;
import com.enterprise.automation.listeners.ExecutionMetadata;
import com.enterprise.automation.utils.TransientFailures;
import java.net.ConnectException;
import java.util.Map;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.Test;

/** Unit tests of the execution engine: retry policy, execution settings, run metadata. */
@Test(groups = "unit")
public class ExecutionEngineTest {

    public void infrastructureFailuresAreTransient() {
        assertThat(TransientFailures.isTransient(new SessionNotCreatedException("no node available"))).isTrue();
        assertThat(TransientFailures.isTransient(new NoSuchSessionException("session deleted"))).isTrue();
        assertThat(TransientFailures.isTransient(new WebDriverException("chrome not reachable"))).isTrue();
        assertThat(TransientFailures.isTransient(new RuntimeException("wrapped", new ConnectException("Connection refused"))))
                .as("causes are inspected").isTrue();
        assertThat(TransientFailures.isTransient(
                new TimeoutException("timeout: Timed out receiving message from renderer: 30.000")))
                .as("browser renderer stalled at start-up").isTrue();
    }

    public void realFailuresAreNeverRetried() {
        assertThat(TransientFailures.isTransient(new AssertionError("expected 3 but was 2"))).isFalse();
        assertThat(TransientFailures.isTransient(new TimeoutException("waiting for toast")))
                .as("the application did not reach the expected state: a real result").isFalse();
        assertThat(TransientFailures.isTransient(new NoSuchElementException("locator changed"))).isFalse();
        assertThat(TransientFailures.isTransient(new IllegalStateException("Expected HTTP 201 but got 500"))).isFalse();
        assertThat(TransientFailures.isTransient(new AssertionError("x", new ConnectException("refused"))))
                .as("an assertion stays an assertion, whatever its cause").isFalse();
    }

    public void executionSettingsComeFromConfiguration() {
        ExecutionSettings defaults = new ConfigLoader(Map.of(), Map.of()).load().runSettings();
        assertThat(defaults).isEqualTo(new ExecutionSettings("classes", 2, 1));

        ExecutionSettings overridden = new ConfigLoader(Map.of("threads", "4", "parallel", "METHODS"),
                Map.of("RETRY_COUNT", "0")).load().runSettings();
        assertThat(overridden).isEqualTo(new ExecutionSettings("methods", 4, 0));

        assertThatThrownBy(() -> new ConfigLoader(Map.of("parallel", "sometimes"), Map.of()).load())
                .hasMessageContaining("Unknown parallel mode");
        assertThatThrownBy(() -> new ConfigLoader(Map.of("retry.count", "-1"), Map.of()).load())
                .hasMessageContaining("retry.count");
    }

    public void metadataDescribesTheRunWithoutSecrets() {
        Map<String, String> metadata = ExecutionMetadata.collect();

        assertThat(metadata).containsKeys("environment", "base.url", "browser", "execution", "parallel", "threads",
                "framework.version", "java.version", "os", "git.commit", "build.number");
        assertThat(metadata.values()).noneMatch(v -> v.contains("Admin@12345") || v.contains("Viewer@12345"));
    }
}
