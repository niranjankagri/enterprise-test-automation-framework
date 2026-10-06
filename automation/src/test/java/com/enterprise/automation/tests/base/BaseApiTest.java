package com.enterprise.automation.tests.base;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.automation.data.CleanupRegistry;
import org.testng.annotations.AfterMethod;

/**
 * Base class of API tests: no browser, sessions for the configured accounts, and the test's
 * registered clean-ups run after every test.
 */
public abstract class BaseApiTest {

    /** The run configuration. */
    protected TestConfig config() {
        return ConfigManager.config();
    }

    /** The API as the admin (cached token). */
    protected ApiSession admin() {
        return ApiSession.admin();
    }

    /** The API as the read-only viewer. */
    protected ApiSession viewer() {
        return ApiSession.viewer();
    }

    /** The API without any token. */
    protected ApiSession anonymous() {
        return ApiSession.anonymous();
    }

    // alwaysRun: clean-ups also run after failed tests and in group-filtered suites
    @AfterMethod(alwaysRun = true)
    public void cleanUp() {
        CleanupRegistry.runAll();
    }
}
