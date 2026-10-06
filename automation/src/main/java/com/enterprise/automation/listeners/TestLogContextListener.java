package com.enterprise.automation.listeners;

import com.enterprise.automation.reporting.ParameterMasking;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Tags every log line with the test that wrote it and logs each test's outcome.
 *
 * <p>With tests running in parallel, log lines of different tests interleave. The test name in
 * the MDC ({@code %X{test}} in logback.xml) lets anyone filter the log to one test.
 */
public class TestLogContextListener implements ITestListener {

    // Key of the test name in the MDC; logback.xml prints it with %X{test}
    public static final String MDC_KEY = "test";
    private static final Logger LOG = LoggerFactory.getLogger(TestLogContextListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        // MDC is per thread: from here on, every log line of this thread carries the test name
        MDC.put(MDC_KEY, name(result));
        LOG.info("START {}{}", name(result), parameters(result));
    }

    // Each outcome logs one line and removes the name, so lines between tests are not mislabelled

    @Override
    public void onTestSuccess(ITestResult result) {
        LOG.info("PASS  {} ({} ms)", name(result), duration(result));
        MDC.remove(MDC_KEY);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOG.error("FAIL  {} ({} ms): {}", name(result), duration(result), String.valueOf(result.getThrowable()));
        MDC.remove(MDC_KEY);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // A retried attempt is reported as skipped; say so instead of printing the infrastructure error again
        String reason = result.wasRetried() ? "retried" : String.valueOf(result.getThrowable());
        LOG.warn("SKIP  {}: {}", name(result), reason);
        MDC.remove(MDC_KEY);
    }

    /** "Class.method", shared with the other listeners. */
    static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
    }

    /** Test parameters for logs, with secret ones (e.g. a {@code password} column) masked. */
    static String parameters(ITestResult result) {
        Object[] parameters = ParameterMasking.mask(
                result.getMethod().getConstructorOrMethod().getMethod(), result.getParameters());
        return parameters.length == 0 ? "" : " " + Arrays.toString(parameters);
    }

    /** Test duration in milliseconds. */
    static long duration(ITestResult result) {
        return result.getEndMillis() - result.getStartMillis();
    }
}
