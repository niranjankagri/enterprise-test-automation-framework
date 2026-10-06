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

    protected TestConfig config() {
        return ConfigManager.config();
    }

    protected ApiSession admin() {
        return ApiSession.admin();
    }

    protected ApiSession viewer() {
        return ApiSession.viewer();
    }

    protected ApiSession anonymous() {
        return ApiSession.anonymous();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanUp() {
        CleanupRegistry.runAll();
    }
}
