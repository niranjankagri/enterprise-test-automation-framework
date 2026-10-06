package com.enterprise.automation.ui.pages;

import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.ui.BasePage;
import com.enterprise.automation.ui.TestId;
import org.openqa.selenium.By;

/** The sign-in screen. */
public class LoginPage extends BasePage {

    private static final By USERNAME = TestId.of("username");
    private static final By PASSWORD = TestId.of("password");
    private static final By LOGIN_BUTTON = TestId.of("login-button");
    private static final By ERROR = TestId.of("login-error");
    private static final By INFO = TestId.of("login-info");

    public LoginPage open() {
        navigateTo("/login.html");
        return waitUntilLoaded();
    }

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
        wait.until(d -> !d.findElement(ERROR).getText().isBlank(), "login error message");
        return actions.text(ERROR);
    }

    /** Message under a field, e.g. {@code fieldError("username")} → "Username is required". */
    public String fieldError(String field) {
        By error = TestId.of(field + "-error");
        wait.until(d -> !d.findElement(error).getText().isBlank(), field + " error");
        return actions.text(error);
    }

    /** Information banner, e.g. after logout. */
    public String infoMessage() {
        return actions.text(INFO);
    }

    private void submit(String username, String password) {
        actions.type(USERNAME, username);
        actions.type(PASSWORD, password);
        actions.click(LOGIN_BUTTON);
    }
}
