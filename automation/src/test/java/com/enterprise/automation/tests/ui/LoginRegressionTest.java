package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.models.UserResponse;
import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.data.UserData;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.pages.CustomerPage;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.LoginPage;
import io.restassured.response.Response;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.testng.annotations.Test;

/** Sign-in rules, data-driven from {@code testdata/login-negative.csv}. */
@Test(groups = {"ui", "regression"})
public class LoginRegressionTest extends BaseTest {

    // One run per row of testdata/login-negative.csv; the provider wraps the password in a report-safe value.
    @Test(dataProvider = "loginNegative", dataProviderClass = TestDataProviders.class)
    public void signInIsRejected(String testCase, String username, TestDataProviders.SensitiveValue password,
                                 String expectedField,
                                 String expectedMessage) {
        LoginPage login = openLoginPage().attemptLogin(username, password.value());

        // "form" = banner from the API (401); a field name = client-side "required" message under that field
        String actual = "form".equals(expectedField) ? login.errorMessage() : login.fieldError(expectedField);
        assertThat(actual).as(testCase).isEqualTo(expectedMessage);
        assertThat(login.currentUrl()).as("still on the login page").contains("/login.html");
        if (!"form".equals(expectedField)) {
            WebElement field = DriverManager.getDriver().findElement(org.openqa.selenium.By.id(expectedField));
            assertThat(field.getAttribute("aria-invalid")).as("invalid login field is identified").isEqualTo("true");
            assertThat(field.getAttribute("aria-describedby")).contains(expectedField + "-error");
            assertThat(DriverManager.getDriver().findElement(
                    org.openqa.selenium.By.id(expectedField + "-error")).getAttribute("role")).isEqualTo("alert");
            assertThat(DriverManager.getDriver().switchTo().activeElement()).isEqualTo(field);
        }
    }

    // Method-level groups are added to the class-level ones (ui, regression + sanity)
    @Test(groups = "sanity")
    public void viewerSignsInWithTheReadOnlyRole() {
        DashboardPage dashboard = loginAsViewer();

        // The header shows the read-only role
        assertThat(dashboard.header().currentUser()).isEqualTo("Vera Viewer (VIEWER)");
    }

    /** Opening a protected page without a session redirects to the login page. */
    public void protectedPagesRedirectToLoginWithoutASession() {
        // A fresh browser has no session: open a protected page directly
        new CustomerPage().visit();

        // The page sends the user to the login page
        assertThat(new LoginPage().waitUntilLoaded().currentUrl()).contains("/login.html");
    }

    /** Login controls have associated labels and remain reachable with visible keyboard focus. */
    @Test(groups = "smoke")
    public void loginFieldsHaveLabelsAndKeyboardFocus() {
        openLoginPage();
        WebDriver driver = DriverManager.getDriver();
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return [...document.querySelectorAll('#login-form input')].every"
                        + "(field => [...document.querySelectorAll('#login-form label')]"
                        + ".some(label => label.htmlFor === field.id));"))
                .as("each login input has an associated label").isEqualTo(Boolean.TRUE);

        Actions keyboard = new Actions(driver);
        keyboard.sendKeys(Keys.TAB).perform();
        assertThat(driver.switchTo().activeElement().getAttribute("id")).isEqualTo("username");
        keyboard.sendKeys(Keys.TAB).perform();
        WebElement password = driver.switchTo().activeElement();
        assertThat(password.getAttribute("id")).isEqualTo("password");
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return [getComputedStyle(document.activeElement).outlineStyle,"
                        + "parseFloat(getComputedStyle(document.activeElement).outlineWidth)];"))
                .as("keyboard focus has a visible outline")
                .satisfies(value -> {
                    assertThat((List<?>) value).first().isEqualTo("solid");
                    assertThat(((Number) ((List<?>) value).get(1)).doubleValue()).isGreaterThanOrEqualTo(1.5);
                });
        AccessibilityAssertions.pageHasNoViolations(driver, "login page");
    }

    /** A deleted account's browser token is cleared and a useful reauthentication message is shown. */
    public void deletedAccountBrowserSessionReturnsToLogin() {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = ApiSession.admin().users().createUser(user);
        CleanupRegistry.register("delete browser session user " + created.id(), () -> {
            Response current = ApiSession.admin().users().get(created.id());
            if (current.statusCode() == 200) {
                ApiSession.admin().users().delete(created.id());
            } else {
                assertThat(current.statusCode()).isEqualTo(404);
            }
        });
        new LoginPage().open().loginAs(new Credentials(user.username(), user.password()));
        assertThat(ApiSession.admin().users().delete(created.id()).statusCode()).isEqualTo(204);

        DriverManager.getDriver().navigate().refresh();
        LoginPage login = new LoginPage().waitUntilLoaded();
        assertThat(login.currentUrl()).contains("expired=1");
        assertThat(login.infoMessage()).contains("session has expired");
    }

    /** A controlled expired-session response clears browser state and redirects to sign-in. */
    @Test(groups = "smoke")
    public void expiredBrowserSessionClearsTheCartAndReturnsToLogin() {
        CustomerPage customers = loginAsAdmin().navigation().openProducts().addToCart("Wireless Mouse")
                .navigation().openCustomers();
        WebDriver driver = DriverManager.getDriver();
        BrowserRequestSimulator.respondWithHttpFailure(driver, "GET", "/api/customers", 401,
                "Synthetic expired session");
        try {
            customers.beginSearch("Ava");
            LoginPage login = new LoginPage().waitUntilLoaded();

            assertThat(login.currentUrl()).contains("expired=1");
            assertThat(login.infoMessage()).contains("session has expired");
            List<?> browserSession = (List<?>) ((JavascriptExecutor) driver).executeScript(
                    "return ['shop.token', 'shop.user', 'shop.cart'].map(key => sessionStorage.getItem(key));");
            assertThat(browserSession).as("expired-session handling clears the token, user and cart")
                    .containsOnlyNulls();
        } finally {
            BrowserRequestSimulator.restore(driver);
        }
    }
}
