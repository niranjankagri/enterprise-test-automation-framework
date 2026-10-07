package com.enterprise.automation.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.OrderPage;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.Map;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The main business flow, end to end through the UI:
 * login → search product → add to cart → checkout → verify the order.
 *
 * <p>Setup and clean-up use the API (fast and independent of the UI): each test buys for its own,
 * freshly created customer, and its orders and the customer are deleted afterwards.
 */
@Test(groups = {"ui", "e2e", "regression"})
public class PurchaseJourneyTest extends BaseTest {

    // Set per test in @BeforeMethod, per thread: TestNG runs a @BeforeMethod on its test's thread,
    // so this also holds with parallel=methods, where tests of this class run at the same time
    private static final ThreadLocal<CustomerData> BUYER = new ThreadLocal<>();

    /** The buyer of the running test. */
    private static CustomerData buyer() {
        return BUYER.get();
    }

    @BeforeMethod(alwaysRun = true)
    public void givenABuyer() {
        // Setup through the API: fast, and not part of what this test checks
        BUYER.set(TestDataFactory.newCustomer());
        CustomerResponse created = ApiSession.admin().customers().createCustomer(buyer());
        // Clean-up: first the buyer's orders (a customer with orders cannot be deleted), then the buyer
        CleanupRegistry.register("delete buyer " + buyer().email() + " and their orders", () -> {
            ApiSession admin = ApiSession.admin();
            for (OrderResponse order : admin.orders().ordersOf(created.id())) {
                admin.orders().deleteOrder(order.id());
            }
            admin.customers().deleteCustomer(created.id());
        });
    }

    public void adminPlacesAnOrderForACustomer() {
        // The order to place: 1 laptop + 2 mice; its expected total comes from the reference catalogue
        OrderData order = TestDataFactory.order(buyer(),
                TestDataFactory.line("LAP-1001", 1), TestDataFactory.line("ACC-3002", 2));

        // Login → search product → add to cart
        DashboardPage dashboard = loginAsAdmin();
        ProductPage products = dashboard.navigation().openProducts();
        for (OrderData.Line line : order.lines()) {
            products.search(line.product().sku()).addToCart(line.product().name());
        }
        products.header().waitForCartCount(order.lines().size()); // one click per product so far

        // Checkout
        CheckoutPage checkout = products.header().openCart();
        for (OrderData.Line line : order.lines()) {
            checkout.setQuantity(line.product().name(), line.quantity());
        }
        assertThat(checkout.total()).isEqualTo(order.displayTotal());
        OrderPage orders = checkout.selectCustomer(buyer().email()).placeOrder();

        // Verify the order
        // The confirmation toast carries the new order's id; its row shows buyer, items, total and status
        long orderId = orders.placedOrderId();
        Map<String, String> row = orders.table().row("Order", "#" + orderId);
        assertThat(row).containsEntry("Customer", buyer().fullName())
                .containsEntry("Items", String.valueOf(order.itemCount()))
                .containsEntry("Total", order.displayTotal())
                .containsEntry("Status", "PLACED");

        // The detail dialog lists the same lines and total
        ModalComponent details = orders.viewOrder(orderId);
        assertThat(details.title()).isEqualTo("Order #" + orderId);
        assertThat(details.text("order-customer")).isEqualTo(buyer().fullName());
        assertThat(details.table("order-items-table").column("Product"))
                .containsExactly(order.lines().stream().map(l -> l.product().name()).toArray(String[]::new));
        assertThat(details.text("order-detail-total")).isEqualTo(order.displayTotal());
        details.cancel();

        assertThat(orders.header().cartCount()).as("cart is emptied after ordering").isZero();
        // Not "in the dashboard's 5 most recent": parallel tests place orders too, so that would be flaky
        assertThat(orders.filterByStatus("PLACED").table().column("Order")).contains("#" + orderId);
    }

    public void adminCancelsAnOrder() {
        // Place a one-line order through the UI
        OrderData order = TestDataFactory.order(buyer(), TestDataFactory.line("AUD-4001", 1));
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart(order.lines().get(0).product().name());
        OrderPage orders = products.header().openCart().selectCustomer(buyer().email()).placeOrder();
        long orderId = orders.placedOrderId();

        // Cancel it from its detail dialog
        orders.cancelOrder(orderId);

        // Confirmed, shown as CANCELLED, and the cancel action is no longer offered
        assertThat(orders.toast().waitForMessage("cancelled")).isEqualTo("Order #" + orderId + " cancelled");
        assertThat(orders.statusOf(orderId)).isEqualTo("CANCELLED");
        assertThat(orders.viewOrder(orderId).hasSubmit()).as("a cancelled order cannot be cancelled again").isFalse();
    }
}
