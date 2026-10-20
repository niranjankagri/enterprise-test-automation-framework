package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.demoapp.DemoApp;
import java.util.Map;
import org.testng.annotations.Test;

/** Pure tests of demo-application startup settings; no application or network is started. */
@Test(groups = "unit")
public class DemoAppOptionsTest {

    /** Remote H2 access defaults off and accepts normalized explicit true/false values. */
    public void remoteDatabaseAccessDefaultsOffAndAcceptsNormalizedValues() {
        DemoApp.Options defaults = DemoApp.Options.fromEnvironment(Map.of());
        DemoApp.Options blank = DemoApp.Options.fromEnvironment(Map.of("DB_ALLOW_REMOTE", "  "));
        DemoApp.Options enabled = DemoApp.Options.fromEnvironment(Map.of("DB_ALLOW_REMOTE", "  TrUe  "));
        DemoApp.Options disabled = DemoApp.Options.fromEnvironment(Map.of("DB_ALLOW_REMOTE", " FaLsE "));

        assertThat(defaults.dbAllowRemote()).isFalse();
        assertThat(blank.dbAllowRemote()).isFalse();
        assertThat(enabled.dbAllowRemote()).isTrue();
        assertThat(disabled.dbAllowRemote()).isFalse();
    }

    /** An invalid remote-access boolean fails with a safe key-specific diagnostic. */
    public void invalidRemoteAccessValueFailsWithoutEchoingIt() {
        String value = "remote-access-synthetic-secret";

        assertThatThrownBy(() -> DemoApp.Options.fromEnvironment(Map.of("DB_ALLOW_REMOTE", value)))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).contains("DB_ALLOW_REMOTE").contains("true or false")
                            .doesNotContain(value);
                });
    }
}
