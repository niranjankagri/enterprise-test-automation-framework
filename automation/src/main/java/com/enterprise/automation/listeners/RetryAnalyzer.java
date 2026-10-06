package com.enterprise.automation.listeners;

import com.enterprise.automation.config.ConfigManager;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a test only when it failed for a transient infrastructure reason
 * ({@link TransientFailures}), at most {@code retry.count} times.
 *
 * <p>TestNG creates one analyzer per test method (and data-provider row), so the attempt counter
 * is per test. Every retry is logged with its cause, so retried tests stay visible: a test that
 * often needs a retry points at an infrastructure problem worth fixing.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(RetryAnalyzer.class);

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public boolean retry(ITestResult result) {
        Throwable failure = result.getThrowable();
        if (failure == null || !TransientFailures.isTransient(failure)) {
            return false;
        }
        int max = ConfigManager.config().runSettings().retryCount();
        int attempt = attempts.incrementAndGet();
        if (attempt > max) {
            LOG.warn("Not retrying {} again: {} retries used", name(result), max);
            return false;
        }
        LOG.warn("Retrying {} ({}/{}) after a transient infrastructure failure: {}", name(result), attempt, max,
                failure.toString());
        return true;
    }

    private static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
    }
}
