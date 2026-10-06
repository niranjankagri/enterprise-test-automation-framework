package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.services.CustomerService;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.CustomerPage;
import java.util.Map;
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

    @Test(groups = "sanity")
    public void adminCreatesACustomer() {
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

    public void customerWithoutOptionalFieldsIsAccepted() {
        // Phone and city are optional: leave them empty
        CustomerData customer = TestDataFactory.newCustomer().withPhone(null).withCity(null);
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        createCustomer(customers, customer);

        // Saved, with empty cells for the optional fields
        Map<String, String> row = customers.search(customer.email()).table().row("Email", customer.email());
        assertThat(row).containsEntry("Phone", "").containsEntry("City", "");
    }

    public void adminEditsACustomer() {
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

    public void adminDeletesACustomer() {
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

    public void cancellingTheFormCreatesNothing() {
        CustomerData customer = TestDataFactory.newCustomer();
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        // Fill the form completely, then Cancel instead of Save
        ModalComponent form = customers.openAddCustomerForm();
        CustomerPage.fillForm(form, customer);
        form.cancel();

        // Nothing was stored
        assertThat(customers.search(customer.email()).table().isEmpty()).isTrue();
    }

    // One run per row of testdata/invalid-customers.csv (the same file drives the API test)
    @Test(dataProvider = "invalidCustomers", dataProviderClass = TestDataProviders.class)
    public void invalidInputIsRejectedPerField(String testCase, String field, String label, String value, String error) {
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        // A valid form with exactly one field made invalid
        ModalComponent form = customers.openAddCustomerForm();
        CustomerPage.fillForm(form, TestDataFactory.newCustomer());
        form.fill(label, value).submit();

        // The API's message for that field appears under it, and the form stays open
        assertThat(form.fieldError(field)).as(testCase).isEqualTo(error);
        assertThat(form.isDisplayed()).as("form stays open").isTrue();
    }

    public void duplicateEmailIsRejected() {
        // A new customer, but with the email of a seeded one
        CustomerData duplicate = TestDataFactory.newCustomer().withEmail("ava.patel@example.com");
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        ModalComponent form = customers.openAddCustomerForm();
        CustomerPage.fillForm(form, duplicate);
        form.submit();

        assertThat(form.fieldError("email")).isEqualTo("Email is already in use");
    }

    @Test(groups = "sanity")
    public void viewerCannotChangeCustomers() {
        CustomerPage customers = loginAsViewer().navigation().openCustomers();

        // Read access yes, write controls no
        assertThat(customers.table().rowCount()).as("viewer can read the list").isPositive();
        assertThat(customers.canAddCustomers()).as("no Add button").isFalse();
        assertThat(customers.canEditOrDeleteCustomers()).as("no Edit/Delete buttons").isFalse();
    }
}
