package com.enterprise.automation.tests.integration;

import static com.enterprise.automation.api.models.OrderRequest.item;
import static com.enterprise.automation.db.DatabaseAssertions.assertNoRow;
import static com.enterprise.automation.db.DatabaseAssertions.assertRow;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.api.models.ProductResponse;
import com.enterprise.automation.api.models.UserResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.data.UserData;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseApiTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * API → database: what the API reports must be what is actually stored. An API can return a
 * correct-looking response and still store something else (wrong column, lost update, stock not
 * written); only the database shows it.
 */
@Test(groups = {"integration", "db", "regression"})
public class ApiToDatabaseTest extends BaseApiTest {

    private ShopDatabase db;

    // In @BeforeClass: no database access -> the whole class is reported as skipped, with the reason
    @BeforeClass(alwaysRun = true)
    public void connect() {
        db = ShopDatabase.fromConfig();
    }

    /** A customer created through the API, removed afterwards (idempotently). */
    private CustomerResponse givenCustomer(CustomerData data) {
        CustomerResponse created = admin().customers().createCustomer(data);
        CleanupRegistry.register("delete customer " + created.id(), () -> admin().customers().deleteCustomerIfExists(created.id()));
        return created;
    }

    /** A product owned by the test, hard-deleted afterwards (the API would only deactivate it). */
    private ProductResponse givenProduct(int stock, String price) {
        ProductResponse product = admin().products().createProduct(
                TestDataFactory.newProduct().withStock(stock).withPrice(new BigDecimal(price)));
        CleanupRegistry.register("remove product " + product.sku(), () -> db.deleteUnusedProduct(product.id()));
        return product;
    }

    @Test(groups = "sanity")
    public void createdCustomerIsStoredExactlyAsSent() {
        CustomerData data = TestDataFactory.newCustomer();

        CustomerResponse created = givenCustomer(data);

        // The stored row, not the API's answer: every column holds what was sent
        assertRow(db.customerById(created.id()), "customer " + created.id())
                .hasValue("first_name", data.firstName())
                .hasValue("last_name", data.lastName())
                .hasValue("email", data.email())
                .hasValue("phone", data.phone())
                .hasValue("city", data.city())
                .hasValue("status", "ACTIVE")
                .hasNonNull("created_at");
    }

    public void updatesAndDeletesReachTheDatabase() {
        CustomerResponse created = givenCustomer(TestDataFactory.newCustomer());

        // PATCH reaches the database...
        admin().customers().update(created.id(), Map.of("city", "Lyon", "status", "INACTIVE"));
        assertRow(db.customerById(created.id()), "updated customer").hasValue("city", "Lyon").hasValue("status", "INACTIVE");

        // ...and so does DELETE (the registered clean-up then finds nothing: idempotent)
        admin().customers().deleteCustomer(created.id());
        assertNoRow(db.customerById(created.id()), "deleted customer " + created.id());
    }

    @Test(groups = "sanity")
    public void placedOrderIsStoredWithItsLinesAndReservesStock() {
        CustomerResponse customer = givenCustomer(TestDataFactory.newCustomer());
        // Own product: price 12.25, stock 10
        ProductResponse product = givenProduct(10, "12.25");

        OrderResponse order = admin().orders().placeOrder(OrderRequest.of(customer.id(), item(product.id(), 3)));
        CleanupRegistry.register("delete order " + order.id(), () -> admin().orders().deleteOrder(order.id()));

        // Header row: customer, status, total 3 x 12.25 = 36.75; one line row; stock 10 - 3
        assertRow(db.orderById(order.id()), "order " + order.id())
                .hasValue("customer_id", customer.id())
                .hasValue("status", "PLACED")
                .hasValue("total", new BigDecimal("36.75"));
        List<Map<String, Object>> items = db.orderItems(order.id());
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("sku", product.sku()).containsEntry("quantity", 3);
        assertThat(db.stockOf(product.sku())).isEqualTo(7);
    }

    public void cancellingAndDeletingOrdersGiveStockBackInTheDatabase() {
        CustomerResponse customer = givenCustomer(TestDataFactory.newCustomer());
        ProductResponse product = givenProduct(4, "5.00");
        OrderResponse cancelled = admin().orders().placeOrder(OrderRequest.of(customer.id(), item(product.id(), 2)));
        OrderResponse deleted = admin().orders().placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));
        CleanupRegistry.register("delete order " + cancelled.id(), () -> admin().orders().deleteOrder(cancelled.id()));
        // Two orders reserved 2 + 1 of 4
        assertThat(db.stockOf(product.sku())).isEqualTo(1);

        // Cancel one, delete the other
        admin().orders().moveTo(cancelled.id(), "CANCELLED");
        admin().orders().deleteOrder(deleted.id());

        // Both gave their stock back; the cancelled order stays (status changed), the deleted one and its lines are gone
        assertThat(db.stockOf(product.sku())).isEqualTo(4);
        assertRow(db.orderById(cancelled.id()), "cancelled order").hasValue("status", "CANCELLED");
        assertNoRow(db.orderById(deleted.id()), "deleted order");
        assertThat(db.orderItems(deleted.id())).as("order lines are deleted with the order").isEmpty();
    }

    public void rejectedOrderLeavesNoTraceInTheDatabase() {
        CustomerResponse customer = givenCustomer(TestDataFactory.newCustomer());
        ProductResponse plenty = givenProduct(50, "1.00");
        ProductResponse scarce = givenProduct(1, "1.00");

        // First line fits, second does not: the API answers 409 (not checked here; the database is)
        admin().orders().create(OrderRequest.of(customer.id(), item(plenty.id(), 5), item(scarce.id(), 2)));

        // No order row, and the first line's stock reservation was rolled back
        assertThat(db.orderCountOf(customer.id())).isZero();
        assertThat(db.stockOf(plenty.sku())).as("transaction rolled back").isEqualTo(50);
    }

    @Test(groups = "security")
    public void passwordsAreStoredHashedAndSalted() {
        // Two different users with the SAME password
        UserData first = TestDataFactory.newUser("VIEWER");
        UserData second = new UserData(TestDataFactory.newUser("VIEWER").username(), first.password(), "Same Password",
                "VIEWER");
        UserResponse a = admin().users().createUser(first);
        UserResponse b = admin().users().createUser(second);
        CleanupRegistry.register("delete users", () -> {
            admin().users().deleteUser(a.id());
            admin().users().deleteUser(b.id());
        });

        Map<String, Object> rowA = assertRow(db.userByUsername(first.username()), "user " + first.username()).row();
        Map<String, Object> rowB = assertRow(db.userByUsername(second.username()), "user " + second.username()).row();

        // No column holds the password; the hash is SHA-256 hex; equal passwords give different hashes (salt)
        assertThat(rowA.values()).as("plain-text password stored").doesNotContain(first.password());
        assertThat(String.valueOf(rowA.get("password_hash"))).matches("[0-9a-f]{64}");
        assertThat(rowA.get("password_hash")).as("same password, different salt, different hash")
                .isNotEqualTo(rowB.get("password_hash"));
    }
}
