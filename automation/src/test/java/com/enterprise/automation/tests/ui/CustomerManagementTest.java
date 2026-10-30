package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.services.CustomerService;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.CustomerPage;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.testng.annotations.Test;

/**
 * Customer management through the UI. Every test creates its own customer with a unique email
 * and registers its removal, so tests never depend on each other or on leftovers.
 */
@Test(groups = {"ui", "regression"})
public class CustomerManagementTest extends BaseTest {

    /** Creates {@code customer} through the form (that is what is tested) and registers its removal. */
    private CustomerPage createCustomer(CustomerPage page, CustomerData customer) {
        registerRemoval(customer);
        page.addCustomer(customer);
        return page;
    }

    /**
     * Clean-up goes through the API: fast, independent of the browser's state, and safe to run
     * even if the UI step failed before the customer existed (then there is nothing to delete).
     */
    private static void registerRemoval(CustomerData customer) {
        CleanupRegistry.register("delete customer " + customer.email(), () -> {
            CustomerService api = ApiSession.admin().customers();
            api.findByEmail(customer.email()).ifPresent(c -> api.deleteCustomer(c.id()));
        });
    }

    /** A customer created through the form shows a toast and appears with exactly the entered values. */
    @Test(groups = "sanity")
    public void tc120_adminCreatesACustomer() {
        // A unique, valid customer and the customer list
        CustomerData customer = TestDataFactory.newCustomer();
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        // Create it through the "Add customer" form
        createCustomer(customers, customer);

        // Confirmation toast, then the row shows exactly what was entered
        assertThat(customers.toast().waitForMessage("created")).isEqualTo("Customer " + customer.fullName() + " created");
        Map<String, String> row = customers.search(customer.email()).table().row("Email", customer.email());
        assertThat(row).containsEntry("Name", customer.fullName())
                .containsEntry("Phone", customer.phone())
                .containsEntry("City", customer.city())
                .containsEntry("Status", "ACTIVE");
    }

    /** A customer without phone and city is saved with empty cells. */
    public void tc121_customerWithoutOptionalFieldsIsAccepted() {
        // Phone and city are optional: leave them empty
        CustomerData customer = TestDataFactory.newCustomer().withPhone(null).withCity(null);
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        createCustomer(customers, customer);

        // Saved, with empty cells for the optional fields
        Map<String, String> row = customers.search(customer.email()).table().row("Email", customer.email());
        assertThat(row).containsEntry("Phone", "").containsEntry("City", "");
    }

    /** The edit form opens pre-filled, and the saved change shows in the list. */
    public void tc122_adminEditsACustomer() {
        CustomerData customer = TestDataFactory.newCustomer();
        CustomerPage customers = createCustomer(loginAsAdmin().navigation().openCustomers(), customer);
        // Same customer, new city
        CustomerData moved = customer.withCity("Lisbon");

        // The edit form opens pre-filled with the stored values
        ModalComponent form = customers.openEditForm(customer.email());
        assertThat(form.title()).isEqualTo("Edit customer");
        assertThat(form.value("Email")).as("form is pre-filled").isEqualTo(customer.email());
        CustomerPage.fillForm(form, moved);
        form.submitAndWaitUntilClosed();

        // Confirmation, and the row shows the new city
        assertThat(customers.toast().waitForMessage("updated")).contains(customer.fullName());
        assertThat(customers.waitUntilLoaded().table().row("Email", customer.email())).containsEntry("City", "Lisbon");
    }

    /** The delete confirmation names the customer, and the customer is gone afterwards. */
    public void tc123_adminDeletesACustomer() {
        CustomerData customer = TestDataFactory.newCustomer();
        registerRemoval(customer); // safety net if the deletion under test fails
        CustomerPage customers = loginAsAdmin().navigation().openCustomers().addCustomer(customer);

        // The confirmation names the customer, then delete
        ModalComponent confirmation = customers.openDeleteConfirmation(customer.email());
        assertThat(confirmation.message()).contains("Delete " + customer.fullName());
        confirmation.submitAndWaitUntilClosed();

        // Confirmation toast, and a search for the email finds nothing
        assertThat(customers.toast().waitForMessage("deleted")).contains(customer.fullName());
        assertThat(customers.waitUntilLoaded().search(customer.email()).table().isEmpty()).isTrue();
    }

    /** Cancelling a filled form creates nothing. */
    public void tc124_cancellingTheFormCreatesNothing() {
        CustomerData customer = TestDataFactory.newCustomer();
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        // Fill the form completely, then Cancel instead of Save
        WebElement addButton = DriverManager.getDriver().findElement(By.cssSelector("[data-testid='add-customer']"));
        ModalComponent form = customers.openAddCustomerForm();
        WebElement dialog = DriverManager.getDriver().findElement(By.cssSelector("[data-testid='modal']"));
        WebElement firstField = DriverManager.getDriver().findElement(By.id("field-firstName"));
        assertThat(dialog.getAttribute("role")).isEqualTo("dialog");
        assertThat(dialog.getAttribute("aria-modal")).isEqualTo("true");
        assertThat(dialog.getAttribute("aria-labelledby")).isEqualTo("modal-title");
        assertThat(DriverManager.getDriver().findElement(By.id("modal-title")).getText()).isEqualTo("Add customer");
        assertThat(DriverManager.getDriver().switchTo().activeElement()).isEqualTo(firstField);
        assertThat(((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                "return [...arguments[0].querySelectorAll('input,select')].every"
                        + "(field => arguments[0].querySelector(`label[for=\"${field.id}\"]`));", dialog))
                .isEqualTo(Boolean.TRUE);
        assertThat(((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                "return [getComputedStyle(document.activeElement).outlineStyle,"
                        + "parseFloat(getComputedStyle(document.activeElement).outlineWidth)];"))
                .as("keyboard focus has a visible outline")
                .satisfies(value -> {
                    assertThat((List<?>) value).first().isEqualTo("solid");
                    assertThat(((Number) ((List<?>) value).get(1)).doubleValue()).isGreaterThanOrEqualTo(1.5);
                });
        new Actions(DriverManager.getDriver()).keyDown(Keys.SHIFT).sendKeys(Keys.TAB).keyUp(Keys.SHIFT).perform();
        assertThat(DriverManager.getDriver().switchTo().activeElement().getAttribute("data-testid"))
                .isEqualTo("modal-submit");
        new Actions(DriverManager.getDriver()).sendKeys(Keys.TAB).perform();
        assertThat(DriverManager.getDriver().switchTo().activeElement()).isEqualTo(firstField);
        CustomerPage.fillForm(form, customer);
        form.cancel();
        assertThat(DriverManager.getDriver().switchTo().activeElement()).isEqualTo(addButton);

        customers.openAddCustomerForm();
        new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
        assertThat(DriverManager.getDriver().findElements(By.cssSelector("[data-testid='modal']"))).isEmpty();
        assertThat(DriverManager.getDriver().switchTo().activeElement()).isEqualTo(addButton);

        // Nothing was stored
        assertThat(customers.search(customer.email()).table().isEmpty()).isTrue();
    }

    /** Customer names containing HTML-shaped input remain literal text in the rendered table. */
    public void tc125_htmlShapedCustomerNameRemainsInertInTheRenderedTable() {
        String hostileName = "<img src=x onerror=window.__tafXss=1>";
        CustomerData customer = TestDataFactory.newCustomer().withFirstName(hostileName);
        registerRemoval(customer);
        CustomerPage customers = loginAsAdmin().navigation().openCustomers().addCustomer(customer);

        Map<String, String> row = customers.table().row("Email", customer.email());
        assertThat(row.get("Name")).isEqualTo(customer.fullName());
        assertThat(DriverManager.getDriver().findElements(By.cssSelector("img[src='x'][onerror]"))).isEmpty();
        assertThat(((JavascriptExecutor) DriverManager.getDriver()).executeScript("return window.__tafXss || null"))
                .isNull();
    }

    // One run per row of testdata/invalid-customers.csv (the same file drives the API test)
    @Test(dataProvider = "invalidCustomers", dataProviderClass = TestDataProviders.class, groups = "smoke")
    public void tc126_invalidInputIsRejectedPerField(String testCase, String field, String label, String value, String error) {
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        // A valid form with exactly one field made invalid
        ModalComponent form = customers.openAddCustomerForm();
        CustomerPage.fillForm(form, TestDataFactory.newCustomer());
        form.fill(label, value).submit();

        // The API's message for that field appears under it, and the form stays open
        assertThat(form.fieldError(field)).as(testCase).isEqualTo(error);
        assertThat(form.isDisplayed()).as("form stays open").isTrue();
        WebElement invalidField = DriverManager.getDriver().findElement(By.id("field-" + field));
        assertThat(invalidField.getAttribute("aria-invalid")).as("invalid control is identified").isEqualTo("true");
        assertThat(invalidField.getAttribute("aria-describedby")).contains("error-" + field);
        assertThat(DriverManager.getDriver().findElement(By.id("error-" + field)).getAttribute("role"))
                .isEqualTo("alert");
        assertThat(DriverManager.getDriver().switchTo().activeElement()).as("focus moves to the first invalid field")
                .isEqualTo(invalidField);
    }

    /** The customer list and add-customer dialog pass the selected automated axe rules. */
    @Test(groups = "smoke")
    public void tc127_customerListAndFormHaveNoAxeViolations() {
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();
        AccessibilityAssertions.pageHasNoViolations(DriverManager.getDriver(), "customer list");

        customers.openAddCustomerForm();
        AccessibilityAssertions.pageHasNoViolations(DriverManager.getDriver(), "add-customer dialog");
    }

    /** An email already in use is rejected in the form. */
    public void tc128_duplicateEmailIsRejected() {
        // A new customer, but with the email of a seeded one
        CustomerData duplicate = TestDataFactory.newCustomer().withEmail("ava.patel@example.com");
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        ModalComponent form = customers.openAddCustomerForm();
        CustomerPage.fillForm(form, duplicate);
        form.submit();

        assertThat(form.fieldError("email")).isEqualTo("Email is already in use");
    }

    /** A viewer sees the list but no add, edit or delete controls. */
    @Test(groups = "sanity")
    public void tc129_viewerCannotChangeCustomers() {
        CustomerPage customers = loginAsViewer().navigation().openCustomers();

        // Read access yes, write controls no
        assertThat(customers.table().rowCount()).as("viewer can read the list").isPositive();
        assertThat(customers.canAddCustomers()).as("no Add button").isFalse();
        assertThat(customers.canEditOrDeleteCustomers()).as("no Edit/Delete buttons").isFalse();
    }

    /** A failed customer-list request is announced, and Retry loads the list without a page reload. */
    @Test(groups = "smoke")
    public void tc130_customerListFailureShowsRetryAndRecovers() {
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();
        var driver = DriverManager.getDriver();
        BrowserRequestSimulator.gateNetworkFailure(driver, "GET", "/api/customers");
        try {
            customers.beginSearch("Ava");
            BrowserRequestSimulator.awaitPending(driver, config().explicitWait());

            assertThat(((JavascriptExecutor) driver).executeScript("return document.body.dataset.busy"))
                    .isEqualTo("true");
            assertThat(driver.findElement(By.cssSelector("[data-testid='loader']")).isDisplayed()).isTrue();

            BrowserRequestSimulator.release(driver);
            customers.waitUntilLoaded();

            assertThat(customers.hasLoadError()).isTrue();
            assertThat(customers.loadErrorMessage()).contains("Customers could not be loaded");
            assertThat(driver.findElement(By.cssSelector("[data-testid='customer-load-retry']")).isEnabled()).isTrue();

            BrowserRequestSimulator.gatePassThrough(driver, "GET", "/api/customers");
            customers.beginRetryLoading();
            BrowserRequestSimulator.awaitPending(driver, config().explicitWait());
            assertThat(customers.hasLoadError()).isFalse();
            assertThat(((JavascriptExecutor) driver).executeScript("return document.body.dataset.busy"))
                    .isEqualTo("true");

            BrowserRequestSimulator.release(driver);
            customers.waitUntilLoaded();

            assertThat(customers.hasLoadError()).isFalse();
            assertThat(customers.table().column("Email")).contains("ava.patel@example.com");
        } finally {
            BrowserRequestSimulator.restore(driver);
        }
    }
}
