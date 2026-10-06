package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import org.testng.annotations.Test;

/** Signing in and out: the entry point of every other UI test. */
@Test(groups = {"ui", "smoke"})
public class LoginSmokeTest extends BaseTest {

    // Class-level @Test: every public method is a test with the class's groups

    public void adminSignsInAndSeesTheDashboard() {
        // Sign in with the configured admin account
        DashboardPage dashboard = loginAsAdmin();

        // The dashboard is shown, greets the user by name, and the header shows name and role
        assertThat(dashboard.heading()).isEqualTo("Dashboard");
        assertThat(dashboard.welcomeMessage()).isEqualTo("Welcome back, Alex Admin.");
        assertThat(dashboard.header().currentUser()).isEqualTo("Alex Admin (ADMIN)");
    }

    public void wrongPasswordIsRejected() {
        // A real username with a wrong password
        LoginPage login = openLoginPage().attemptLogin(config().admin().username(), "wrong-password");

        // The API's message is shown and the user stays on the login page
        assertThat(login.errorMessage()).isEqualTo("Invalid username or password");
        assertThat(login.currentUrl()).contains("/login.html");
    }

    public void signingOutReturnsToTheLoginPage() {
        // Sign in, then use the header's "Log out"
        LoginPage login = loginAsAdmin().header().logout();

        // Back on the login page, with a confirmation banner
        assertThat(login.infoMessage()).isEqualTo("You have been logged out.");
    }
}
