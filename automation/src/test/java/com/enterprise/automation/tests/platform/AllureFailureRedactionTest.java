package com.enterprise.automation.tests.platform;

import com.enterprise.automation.reporting.Report;
import org.testng.annotations.Test;

/** A deliberately failing fixture used by the persisted Allure failure-redaction verifier. */
public class AllureFailureRedactionTest {

    /** Leaves a real TestNG failure for the verifier to inspect after Allure finishes writing the result. */
    @Test(groups = {"platform", "security", "allure-failure-redaction"})
    public void intentionallyFailsWithSyntheticUrlSecrets() {
        String marker = System.getProperty("synthetic.url.marker");
        if (marker == null || marker.isBlank()) {
            throw new IllegalStateException("The synthetic URL marker must be supplied by the verifier");
        }
        String url = "https://synthetic-user-" + marker + ":synthetic-password-" + marker
                + "@redaction.example.test/path?access_token=synthetic-token-" + marker + "&page=2";
        String typedPassword = "synthetic-typed-password-" + marker;
        IllegalStateException failure = new IllegalStateException(
                "Synthetic navigation failed at " + url + " with password=" + typedPassword);
        Report.step("Navigate to " + url, () -> {
            throw failure;
        });
    }
}
