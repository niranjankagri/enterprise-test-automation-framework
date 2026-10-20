package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.testng.annotations.Test;

/** Keeps the unit suite independent from the live demo application and database. */
@Test(groups = "unit")
public class UnitSuiteIsolationTest {

    /** The unit suite must not register the listener that starts the application under test. */
    @Test
    public void unitSuiteDoesNotRegisterApplicationLifecycle() throws IOException {
        try (InputStream suiteResource = getClass().getResourceAsStream("/suites/unit.xml")) {
            assertThat(suiteResource).as("unit TestNG suite resource").isNotNull();
            String suite = new String(suiteResource.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(suite).contains("<include name=\"unit\"/>")
                    .doesNotContain("com.enterprise.automation.tests.base.DemoAppLifecycle");
        }
    }
}
