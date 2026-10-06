package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.pages.CustomerPage;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import org.testng.annotations.Test;

/** Sign-in rules, data-driven from {@code testdata/login-negative.csv}. */
@Test(groups = {"ui", "regression"})
public class LoginRegressionTest extends BaseTest {

    // One run per row of testdata/login-negative.csv; the password parameter is masked in logs and reports
    @Test(dataProvider = "loginNegative", dataProviderClass = TestDataProviders.class)
    public void signInIsRejected(String testCase, String username, String password, String expectedField,
                                 String expectedMessage) {
        LoginPage login = openLoginPage().attemptLogin(username, password);

        // "form" = banner from the API (401); a field name = client-side "required" message under that field
        String actual = "form".equals(expectedField) ? login.errorMessage() : login.fieldError(expectedField);
        assertThat(actual).as(testCase).isEqualTo(expectedMessage);
        assertThat(login.currentUrl()).as("still on the login page").contains("/login.html");
    }

    // Method-level groups are added to the class-level ones (ui, regression + sanity)
    @Test(groups = "sanity")
    public void viewerSignsInWithTheReadOnlyRole() {
        DashboardPage dashboard = loginAsViewer();

        // The header shows the read-only role
        assertThat(dashboard.header().currentUser()).isEqualTo("Vera Viewer (VIEWER)");
    }

    public void protectedPagesRedirectToLoginWithoutASession() {
        // A fresh browser has no session: open a protected page directly
        new CustomerPage().visit();

        // The page sends the user to the login page
        assertThat(new LoginPage().waitUntilLoaded().currentUrl()).contains("/login.html");
    }
}
