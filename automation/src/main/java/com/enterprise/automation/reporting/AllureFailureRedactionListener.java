package com.enterprise.automation.reporting;

import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import java.util.List;

/** Redacts sensitive URL data from test and step failure details before Allure writes a result file. */
public class AllureFailureRedactionListener implements TestLifecycleListener {

    /** Scrubs all failure detail fields at the final persisted-result boundary. */
    @Override
    public void beforeTestWrite(TestResult result) {
        sanitize(result.getStatusDetails());
        sanitizeSteps(result.getSteps());
    }

    private static void sanitizeSteps(List<StepResult> steps) {
        if (steps == null) {
            return;
        }
        for (StepResult step : steps) {
            sanitize(step.getStatusDetails());
            sanitizeSteps(step.getSteps());
        }
    }

    private static void sanitize(StatusDetails details) {
        if (details == null) {
            return;
        }
        details.setMessage(SecretMasker.mask(details.getMessage()));
        details.setTrace(SecretMasker.mask(details.getTrace()));
        details.setActual(SecretMasker.mask(details.getActual()));
        details.setExpected(SecretMasker.mask(details.getExpected()));
    }
}
