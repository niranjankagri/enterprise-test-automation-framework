package com.enterprise.automation.tests.integration;

import static com.enterprise.automation.db.DatabaseAssertions.assertRow;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.CustomerPage;
import com.enterprise.automation.ui.pages.OrderPage;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.List;
import java.util.Map;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * One business record checked through all three layers. A defect can hide in any of them: the UI
 * shows a cached value, the API maps a field wrongly, the database keeps an old row. Checking the
 * same data in UI, API and database catches what single-layer tests miss.
 */
@Test(groups = {"integration", "db", "ui", "e2e", "regression"})
public class UiApiDatabaseTest extends BaseTest {

    private ShopDatabase db;

    // No database access in this environment -> the class is skipped with the reason
    @BeforeClass(alwaysRun = true)
    public void connect() {
        db = ShopDatabase.fromConfig();
    }

    /**
     * Clean-up by email, registered before the customer exists: it removes whatever the test
     * managed to create (the customer's orders first, then the customer), or nothing.
     */
    private static void registerRemoval(String email) {
        CleanupRegistry.register("delete customer " + email + " and their orders", () -> {
            ApiSession admin = ApiSession.admin();
            admin.customers().findByEmail(email).ifPresent(c -> {
                admin.orders().ordersOf(c.id()).forEach(o -> admin.orders().deleteOrder(o.id()));
                admin.customers().deleteCustomer(c.id());
            });
        });
    }

    /**
     * Create through the API → verify the response → verify the database → find it in the UI.
     */
    @Test(groups = "sanity")
    public void customerCreatedThroughTheApiIsStoredAndVisibleInTheUi() {
        CustomerData customer = TestDataFactory.newCustomer();
        registerRemoval(customer.email());

        // API
        CustomerResponse created = ApiSession.admin().customers().createCustomer(customer);
        assertThat(created.email()).isEqualTo(customer.email());
        assertThat(created.status()).isEqualTo("ACTIVE");

        // Database
        assertRow(db.customerByEmail(customer.email()), "customer " + customer.email())
                .hasValue("id", created.id())
                .hasValue("first_name", customer.firstName())
                .hasValue("city", customer.city());

        // UI
        CustomerPage customers = loginAsAdmin().navigation().openCustomers().search(customer.email());
        assertThat(customers.table().row("Email", customer.email()))
                .containsEntry("Name", customer.fullName())
                .containsEntry("City", customer.city())
                .containsEntry("Status", "ACTIVE");
    }

    /** The other direction: change through the UI, verify through the API and in the database. */
    public void customerChangedInTheUiIsVisibleThroughTheApiAndStored() {
        CustomerData customer = TestDataFactory.newCustomer();
        registerRemoval(customer.email());
        CustomerPage customers = loginAsAdmin().navigation().openCustomers().addCustomer(customer);

        // UI: change city and status through the edit form
        ModalComponent form = customers.openEditForm(customer.email());
        form.fill("City", "Kyoto").fill("Status", "Inactive");
        form.submitAndWaitUntilClosed();

        // API: the change is visible through the REST API ... Database: ... and stored
        CustomerResponse viaApi = ApiSession.admin().customers().findByEmail(customer.email()).orElseThrow();
        assertThat(viaApi.city()).isEqualTo("Kyoto");
        assertThat(viaApi.status()).isEqualTo("INACTIVE");
        assertRow(db.customerById(viaApi.id()), "customer " + viaApi.id())
                .hasValue("city", "Kyoto")
                .hasValue("status", "INACTIVE");
    }

    /** An order placed in the UI: API returns it, the database holds its lines and the stock moved. */
    public void orderPlacedInTheUiIsConsistentInApiAndDatabase() {
        CustomerData customer = TestDataFactory.newCustomer();
        registerRemoval(customer.email());
        ApiSession.admin().customers().createCustomer(customer);
        OrderData order = TestDataFactory.order(customer, TestDataFactory.line("MON-2002", 2));
        String sku = order.lines().get(0).product().sku();
        // Stock before ordering, read straight from the database
        int stockBefore = db.stockOf(sku);

        ProductPage products = loginAsAdmin().navigation().openProducts().search(sku)
                .addToCart(order.lines().get(0).product().name());
        OrderPage orders = products.header().openCart().setQuantity(order.lines().get(0).product().name(), 2)
                .selectCustomer(customer.email()).placeOrder();
        // UI: the id of the order the checkout created
        long orderId = orders.placedOrderId();

        // API: same total and line; Database: header, line and stock decrease
        OrderResponse viaApi = ApiSession.admin().orders().getOrder(orderId);
        assertThat(viaApi.total()).isEqualByComparingTo(order.total());
        assertThat(viaApi.items()).extracting(OrderResponse.Item::sku).containsExactly(sku);

        assertRow(db.orderById(orderId), "order " + orderId).hasValue("total", order.total()).hasValue("status", "PLACED");
        List<Map<String, Object>> lines = db.orderItems(orderId);
        assertThat(lines).singleElement().satisfies(l -> assertThat(l).containsEntry("quantity", 2));
        assertThat(db.stockOf(sku)).isEqualTo(stockBefore - 2);
    }
}
