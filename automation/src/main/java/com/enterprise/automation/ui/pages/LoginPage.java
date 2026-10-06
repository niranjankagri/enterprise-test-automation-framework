package com.enterprise.automation.ui.pages;

import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.ui.BasePage;
import com.enterprise.automation.ui.TestId;
import org.openqa.selenium.By;

/** The sign-in screen. */
public class LoginPage extends BasePage {

    // Form fields, button and the two message areas (error banner, info banner)
    private static final By USERNAME = TestId.of("username");
    private static final By PASSWORD = TestId.of("password");
    private static final By LOGIN_BUTTON = TestId.of("login-button");
    private static final By ERROR = TestId.of("login-error");
    private static final By INFO = TestId.of("login-info");

    /** Opens the login page by URL. */
    public LoginPage open() {
        navigateTo("/login.html");
        return waitUntilLoaded();
    }

    /** The login page is not a ShopPage (no header, no data-ready): ready = URL + button visible. */
    public LoginPage waitUntilLoaded() {
        wait.urlContains("/login.html");
        wait.visible(LOGIN_BUTTON);
        return this;
    }

    /** Signs in and expects to land on the dashboard. */
    public DashboardPage loginAs(Credentials credentials) {
        submit(credentials.username(), credentials.password());
        return new DashboardPage().waitUntilLoaded();
    }

    /** Signs in and expects to stay on this page (wrong or missing credentials). */
    public LoginPage attemptLogin(String username, String password) {
        submit(username, password);
        return this;
    }

    /** Message for rejected credentials, e.g. "Invalid username or password". */
    public String errorMessage() {
        // The banner exists empty; wait until the API's answer has been written into it
        wait.until(d -> !d.findElement(ERROR).getText().isBlank(), "login error message");
        return actions.text(ERROR);
    }

    /** Message under a field, e.g. {@code fieldError("username")} → "Username is required". */
    public String fieldError(String field) {
        // Test ids follow the pattern "<field>-error"
        By error = TestId.of(field + "-error");
        wait.until(d -> !d.findElement(error).getText().isBlank(), field + " error");
        return actions.text(error);
    }

    /** Information banner, e.g. after logout. */
    public String infoMessage() {
        return actions.text(INFO);
    }

    /** Fills both fields and clicks "Sign in" (the password is masked in the report). */
    private void submit(String username, String password) {
        actions.type(USERNAME, username);
        actions.type(PASSWORD, password);
        actions.click(LOGIN_BUTTON);
    }
}
