package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import org.testng.annotations.Test;

/** Signing in and out: the entry point of every other UI test. */
@Test(groups = {"ui", "smoke"})
public class LoginSmokeTest extends BaseTest {

    public void adminSignsInAndSeesTheDashboard() {
        DashboardPage dashboard = loginAsAdmin();

        assertThat(dashboard.heading()).isEqualTo("Dashboard");
        assertThat(dashboard.welcomeMessage()).isEqualTo("Welcome back, Alex Admin.");
        assertThat(dashboard.header().currentUser()).isEqualTo("Alex Admin (ADMIN)");
    }

    public void wrongPasswordIsRejected() {
        LoginPage login = openLoginPage().attemptLogin(config().admin().username(), "wrong-password");

        assertThat(login.errorMessage()).isEqualTo("Invalid username or password");
        assertThat(login.currentUrl()).contains("/login.html");
    }

    public void signingOutReturnsToTheLoginPage() {
        LoginPage login = loginAsAdmin().header().logout();

        assertThat(login.infoMessage()).isEqualTo("You have been logged out.");
    }
}
