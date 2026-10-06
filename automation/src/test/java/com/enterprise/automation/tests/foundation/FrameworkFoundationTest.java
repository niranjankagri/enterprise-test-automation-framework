package com.enterprise.automation.tests.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.FrameworkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Proves the foundation works before any real test is written: the build filters resources,
 * TestNG runs, AssertJ asserts and SLF4J/Logback logs.
 */
public class FrameworkFoundationTest {

    private static final Logger LOG = LoggerFactory.getLogger(FrameworkFoundationTest.class);

    @Test(description = "Framework identity is filled in from the Maven build")
    public void frameworkIdentityComesFromTheBuild() {
        LOG.info("Running {} {}", FrameworkInfo.name(), FrameworkInfo.version());

        assertThat(FrameworkInfo.name()).isEqualTo("Enterprise Test Automation Framework");
        assertThat(FrameworkInfo.version())
                .as("version is filtered by Maven, not left as a ${...} placeholder")
                .isNotBlank()
                .doesNotContain("${");
    }
}
