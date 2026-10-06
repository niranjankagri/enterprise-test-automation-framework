package com.enterprise.automation.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.OrderPage;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.Map;
import org.testng.annotations.Test;

/**
 * The main business flow, end to end through the UI:
 * login → search product → add to cart → checkout → verify the order.
 */
@Test(groups = {"ui", "e2e", "regression"})
public class PurchaseJourneyTest extends BaseTest {

    /** Seeded customer: placing an order does not change the customer itself. */
    private static final CustomerData BUYER = new CustomerData("Liam", "Chen", "liam.chen@example.com", null, null);

    public void adminPlacesAnOrderForACustomer() {
        OrderData order = TestDataFactory.order(BUYER,
                TestDataFactory.line("LAP-1001", 1), TestDataFactory.line("ACC-3002", 2));

        // Login → search product → add to cart
        DashboardPage dashboard = loginAsAdmin();
        ProductPage products = dashboard.navigation().openProducts();
        for (OrderData.Line line : order.lines()) {
            products.search(line.product().sku()).addToCart(line.product().name());
        }
        products.header().waitForCartCount(order.lines().size()); // one click per product so far

        // Checkout
        var checkout = products.header().openCart();
        for (OrderData.Line line : order.lines()) {
            checkout.setQuantity(line.product().name(), line.quantity());
        }
        assertThat(checkout.total()).isEqualTo(order.displayTotal());
        OrderPage orders = checkout.selectCustomer(BUYER.email()).placeOrder();

        // Verify the order
        long orderId = orders.placedOrderId();
        CleanupRegistry.register("cancel order " + orderId, () -> new OrderPage().open().cancelOrder(orderId));

        Map<String, String> row = orders.table().row("Order", "#" + orderId);
        assertThat(row).containsEntry("Customer", BUYER.fullName())
                .containsEntry("Items", String.valueOf(order.itemCount()))
                .containsEntry("Total", order.displayTotal())
                .containsEntry("Status", "PLACED");

        ModalComponent details = orders.viewOrder(orderId);
        assertThat(details.title()).isEqualTo("Order #" + orderId);
        assertThat(details.text("order-customer")).isEqualTo(BUYER.fullName());
        assertThat(details.table("order-items-table").column("Product"))
                .containsExactly(order.lines().stream().map(l -> l.product().name()).toArray(String[]::new));
        assertThat(details.text("order-detail-total")).isEqualTo(order.displayTotal());
        details.cancel();

        assertThat(orders.header().cartCount()).as("cart is emptied after ordering").isZero();
        assertThat(orders.navigation().openDashboard().recentOrders().column("Order")).contains("#" + orderId);
    }

    public void adminCancelsAnOrder() {
        OrderData order = TestDataFactory.order(BUYER, TestDataFactory.line("AUD-4001", 1));
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart(order.lines().get(0).product().name());
        OrderPage orders = products.header().openCart().selectCustomer(BUYER.email()).placeOrder();
        long orderId = orders.placedOrderId();

        orders.cancelOrder(orderId);

        assertThat(orders.toast().waitForMessage("cancelled")).isEqualTo("Order #" + orderId + " cancelled");
        assertThat(orders.statusOf(orderId)).isEqualTo("CANCELLED");
        assertThat(orders.viewOrder(orderId).hasSubmit()).as("a cancelled order cannot be cancelled again").isFalse();
    }
}
