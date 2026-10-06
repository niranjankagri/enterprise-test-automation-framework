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

    @Test(dataProvider = "loginNegative", dataProviderClass = TestDataProviders.class)
    public void signInIsRejected(String testCase, String username, String password, String expectedField,
                                 String expectedMessage) {
        LoginPage login = openLoginPage().attemptLogin(username, password);

        String actual = "form".equals(expectedField) ? login.errorMessage() : login.fieldError(expectedField);
        assertThat(actual).as(testCase).isEqualTo(expectedMessage);
        assertThat(login.currentUrl()).as("still on the login page").contains("/login.html");
    }

    @Test(groups = "sanity")
    public void viewerSignsInWithTheReadOnlyRole() {
        DashboardPage dashboard = loginAsViewer();

        assertThat(dashboard.header().currentUser()).isEqualTo("Vera Viewer (VIEWER)");
    }

    public void protectedPagesRedirectToLoginWithoutASession() {
        new CustomerPage().visit();

        assertThat(new LoginPage().waitUntilLoaded().currentUrl()).contains("/login.html");
    }
}
