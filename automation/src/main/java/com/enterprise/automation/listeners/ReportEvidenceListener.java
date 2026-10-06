package com.enterprise.automation.listeners;

import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.reporting.ParameterMasking;
import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.reporting.TestLogAppender;
import com.enterprise.automation.utils.BrowserConsole;
import com.enterprise.automation.utils.ScreenshotUtils;
import io.qameta.allure.Allure;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Puts the evidence of every test into the report, without any code in the tests:
 * <ul>
 *   <li>labels: layer (epic) from the package, feature from the class, groups as tags;</li>
 *   <li>the test's own log lines;</li>
 *   <li>on failure: screenshot (also saved under {@code target/screenshots}), URL, page source and
 *       the browser console (Chrome/Edge).</li>
 * </ul>
 *
 * <p>The failure evidence is taken in {@link #afterInvocation}, which TestNG calls right after the
 * test method and before {@code @AfterMethod}. {@code ITestListener.onTestFailure} would be too
 * late: the base test has already quit the browser by then.
 */
public class ReportEvidenceListener implements IInvokedMethodListener, ITestListener {

    // Last package segment of a test class -> report epic (the layer it tests)
    private static final Map<String, String> LAYERS = Map.of(
            "api", "API", "ui", "UI", "db", "Database", "integration", "Integration",
            "e2e", "End-to-end", "platform", "Framework", "foundation", "Framework");

    @Override
    public void onTestStart(ITestResult result) {
        // Collect this thread's log lines from now on
        TestLogAppender.startCapture();
        // e.g. com.enterprise.automation.tests.api -> "api" -> epic "API"
        String pkg = result.getTestClass().getRealClass().getPackageName();
        String layer = pkg.substring(pkg.lastIndexOf('.') + 1);
        Allure.epic(LAYERS.getOrDefault(layer, layer));
        // CustomerApiTest -> feature "CustomerApi"
        Allure.feature(result.getTestClass().getRealClass().getSimpleName().replaceAll("Test$", ""));
        // Groups (smoke, regression, ...) become tags, filterable in the report
        for (String group : result.getMethod().getGroups()) {
            Allure.label("tag", group);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        // Also called after @Before/@After methods: only test methods are of interest here
        if (!method.isTestMethod()) {
            return;
        }
        // Data-driven parameters such as a "password" column must not appear in the report
        Allure.getLifecycle().updateTest(testCase -> testCase.getParameters().stream()
                .filter(p -> ParameterMasking.isSecret(p.getName()))
                .forEach(p -> p.setValue("****")));
        // Browser evidence only for failed UI tests (API/unit tests have no browser)
        if (result.getStatus() == ITestResult.FAILURE && DriverManager.hasDriver()) {
            attachBrowserEvidence(result);
        }
        // Every test, passed or failed, gets its own log
        String log = TestLogAppender.drainCapture();
        if (!log.isEmpty()) {
            Report.attachText("Test log", log);
        }
    }

    /** Screenshot (file + report), URL, page source and console of this thread's browser. */
    private static void attachBrowserEvidence(ITestResult result) {
        WebDriver driver = DriverManager.getDriver();
        String name = result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
        // As a file for people without the report, and attached to the report
        ScreenshotUtils.save(driver, name);
        Report.attachPng("Screenshot at failure", ScreenshotUtils.capture(driver));
        // A crashed browser cannot answer: note it instead of failing again
        try {
            Report.attachText("Page URL", driver.getCurrentUrl());
            Report.attachHtml("Page source", driver.getPageSource());
            // JavaScript errors and failed requests often explain a UI failure (Chrome/Edge only)
            BrowserConsole.read(driver).ifPresent(console -> Report.attachText("Browser console", console));
        } catch (RuntimeException e) {
            Report.attachText("Browser evidence unavailable", e.toString());
        }
    }
}
