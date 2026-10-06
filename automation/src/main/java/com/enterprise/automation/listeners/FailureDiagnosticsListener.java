package com.enterprise.automation.listeners;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Writes one self-contained block per failed test: what failed, where, when, on which
 * environment and browser, with which data, on which thread, and why. Enough to start an
 * investigation from the log alone; the screenshot (UI) and API calls are logged next to it.
 */
public class FailureDiagnosticsListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(FailureDiagnosticsListener.class);

    @Override
    public void onTestFailure(ITestResult result) {
        TestConfig config = ConfigManager.config();
        Throwable failure = result.getThrowable();
        LOG.error("""
                ---- FAILURE DIAGNOSTICS ----
                Test:        {}
                Parameters:  {}
                Groups:      {}
                Thread:      {}
                Duration:    {} ms
                Environment: {} ({})
                Browser:     {}{} via {}
                Transient:   {}
                Failure:     {}
                -----------------------------""",
                TestLogContextListener.name(result),
                TestLogContextListener.parameters(result).isEmpty() ? "-" : TestLogContextListener.parameters(result).trim(),
                String.join(", ", result.getMethod().getGroups()),
                Thread.currentThread().getName(),
                TestLogContextListener.duration(result),
                config.environment(), config.baseUrl(),
                config.browser(), config.headless() ? " (headless)" : "", config.execution(),
                failure != null && TransientFailures.isTransient(failure) ? "yes (retry candidate)" : "no",
                String.valueOf(failure));
    }
}
