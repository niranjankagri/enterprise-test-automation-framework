package com.enterprise.automation.ui.pages;

import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;

/** Customer list: search, add, edit and delete. Rows are identified by their unique email. */
public class CustomerPage extends ShopPage<CustomerPage> {

    // Toolbar elements
    private static final By SEARCH = TestId.of("customer-search");
    private static final By SEARCH_BUTTON = TestId.of("customer-search-button");
    private static final By ADD = TestId.of("add-customer");
    private static final By RESULT_COUNT = TestId.of("result-count");
    // Column that identifies a customer row (emails are unique)
    private static final String EMAIL = "Email";

    @Override
    protected String pageId() {
        return "customers";
    }

    @Override
    protected String path() {
        return "/customers.html";
    }

    /** The customer list. */
    public TableComponent table() {
        return new TableComponent(TestId.of("customers-table"));
    }

    /** Searches by name, email or city and waits for the filtered list. */
    public CustomerPage search(String term) {
        actions.type(SEARCH, term);
        actions.click(SEARCH_BUTTON);
        // Clicking sets data-ready="false" synchronously, so this waits for the new result
        return waitUntilLoaded();
    }

    /** E.g. "3 customers". */
    public String resultCount() {
        return actions.text(RESULT_COUNT);
    }

    /** Only admins see the "Add customer" button. */
    public boolean canAddCustomers() {
        return actions.isDisplayed(ADD);
    }

    /** Only admins get Edit and Delete buttons in the rows. */
    public boolean canEditOrDeleteCustomers() {
        // Immediate check on the loaded list: any Edit or Delete button at all?
        return !driver.findElements(TestId.of("edit")).isEmpty() || !driver.findElements(TestId.of("delete")).isEmpty();
    }

    /** Opens the empty "Add customer" dialog (for tests that fill it themselves, e.g. negative cases). */
    public ModalComponent openAddCustomerForm() {
        actions.click(ADD);
        return new ModalComponent();
    }

    /** Fills and saves the "Add customer" form; phone and city may be {@code null}. */
    public CustomerPage addCustomer(String firstName, String lastName, String email, String phone, String city) {
        ModalComponent form = openAddCustomerForm();
        fillForm(form, firstName, lastName, email, phone, city);
        // The dialog closes only after the save succeeded and the list reloaded
        form.submitAndWaitUntilClosed();
        return waitUntilLoaded();
    }

    /** Adds {@code customer} through the form and waits for the list to reload. */
    public CustomerPage addCustomer(CustomerData customer) {
        return addCustomer(customer.firstName(), customer.lastName(), customer.email(), customer.phone(),
                customer.city());
    }

    /** Fills the customer form from a data record. */
    public static void fillForm(ModalComponent form, CustomerData customer) {
        fillForm(form, customer.firstName(), customer.lastName(), customer.email(), customer.phone(), customer.city());
    }

    /** Clicks Edit in the row of {@code email}; returns the pre-filled dialog. */
    public ModalComponent openEditForm(String email) {
        table().clickInRow(EMAIL, email, TestId.of("edit"));
        return new ModalComponent();
    }

    /** Clicks Delete in the row of {@code email}; returns the confirmation dialog. */
    public ModalComponent openDeleteConfirmation(String email) {
        table().clickInRow(EMAIL, email, TestId.of("delete"));
        return new ModalComponent();
    }

    /** Deletes the customer through the UI (confirm included). */
    public CustomerPage deleteCustomer(String email) {
        openDeleteConfirmation(email).submitAndWaitUntilClosed();
        return waitUntilLoaded();
    }

    /** Whether the current list contains this email. */
    public boolean hasCustomer(String email) {
        return table().hasRow(EMAIL, email);
    }

    /** Fills the customer form; shared by add and edit. */
    public static void fillForm(ModalComponent form, String firstName, String lastName, String email, String phone,
                                String city) {
        // Fields by their visible labels; null optional values leave the field empty
        form.fill("First name", firstName)
                .fill("Last name", lastName)
                .fill("Email", email)
                .fill("Phone", phone == null ? "" : phone)
                .fill("City", city == null ? "" : city);
    }
}
