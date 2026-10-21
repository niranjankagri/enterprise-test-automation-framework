package com.enterprise.automation.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.ui.BrowserRequestSimulator;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.OrderPage;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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

    /** A new buyer for every test, created through the API and removed afterwards with their orders. */
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

    /**
     * The whole purchase through the UI: log in, add products to the cart, choose the buyer and place the order, then
     * check the result.
     */
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
        assertThat(DriverManager.getDriver().findElement(By.cssSelector("[data-testid='checkout-customer']"))
                .getAttribute("aria-label")).isEqualTo("Customer");
        assertThat(DriverManager.getDriver().findElements(By.cssSelector("[data-testid='quantity']")))
                .allSatisfy(quantity -> assertThat(quantity.getAttribute("aria-label")).startsWith("Quantity of "));
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

    /** A stale cart cannot buy stock that was consumed after the product was displayed. */
    public void checkoutWithStaleStockPreservesTheCartAndCreatesNoOrder() {
        assertRejectedCheckoutPreservesState(false);
    }

    /** A product deactivated after it was displayed cannot be bought from the stale cart. */
    public void checkoutWithInactiveProductPreservesTheCartAndCreatesNoOrder() {
        assertRejectedCheckoutPreservesState(true);
    }

    /** A second submit while the first request is pending cannot create a duplicate order. */
    public void repeatedCheckoutSubmissionCreatesOneOrder() {
        ProductData productData = TestDataFactory.newProduct().withStock(4);
        var product = ApiSession.admin().products().createProduct(productData);
        CleanupRegistry.register("deactivate product " + product.id(),
                () -> ApiSession.admin().products().deleteProduct(product.id()));
        ProductPage products = loginAsAdmin().navigation().openProducts();
        products.search(product.sku()).addToCart(product.name());
        products.header().openCart().selectCustomer(buyer().email());
        var driver = DriverManager.getDriver();
        ((JavascriptExecutor) driver).executeScript("""
                const originalFetch = window.fetch.bind(window);
                let releaseOrder;
                window.__orderGate = new Promise(resolve => { releaseOrder = resolve; });
                window.__releaseOrder = releaseOrder;
                window.fetch = (url, options) => String(url) === '/api/orders'
                  ? window.__orderGate.then(() => originalFetch(url, options))
                  : originalFetch(url, options);
                """);

        var submit = driver.findElement(By.cssSelector("[data-testid='place-order']"));
        submit.click();
        assertThat(submit.isEnabled()).as("first request disables the submit control").isFalse();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submit);
        ((JavascriptExecutor) driver).executeScript("window.__releaseOrder();");

        var orders = new com.enterprise.automation.ui.pages.OrderPage().waitUntilLoaded();
        long orderId = orders.placedOrderId();
        List<OrderResponse> buyerOrders = buyerOrders();
        assertThat(buyerOrders).extracting(OrderResponse::id).containsExactly(orderId);
        assertThat(buyerOrders.get(0).items()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(1));
    }

    /** A failed order request leaves no writes behind, restores the control and can be retried safely. */
    @Test(groups = "smoke")
    public void failedOrderSubmissionCanBeRetriedWithoutPartialWrites() {
        ShopDatabase db = ShopDatabase.fromConfig();
        ProductData productData = TestDataFactory.newProduct().withStock(3);
        var product = ApiSession.admin().products().createProduct(productData);
        CleanupRegistry.register("deactivate product " + product.id(),
                () -> ApiSession.admin().products().deleteProduct(product.id()));
        ProductPage products = loginAsAdmin().navigation().openProducts();
        products.search(product.sku()).addToCart(product.name());
        CheckoutPage checkout = products.header().openCart().selectCustomer(buyer().email());
        long buyerId = ApiSession.admin().customers().findByEmail(buyer().email()).orElseThrow().id();
        var driver = DriverManager.getDriver();

        BrowserRequestSimulator.gateHttpFailure(driver, "POST", "/api/orders", 503,
                "Synthetic service unavailable");
        try {
            checkout.beginPlaceOrder();
            BrowserRequestSimulator.awaitPending(driver, config().explicitWait());

            assertThat(checkout.canPlaceOrder()).as("submission is disabled while the request is pending").isFalse();
            assertThat(((JavascriptExecutor) driver).executeScript("return document.body.dataset.busy"))
                    .isEqualTo("true");
            BrowserRequestSimulator.release(driver);

            assertThat(checkout.waitForErrorMessage()).isEqualTo("Synthetic service unavailable");
            assertThat(checkout.canPlaceOrder()).as("submission is available again after failure").isTrue();
            assertThat(checkout.cart().column("Product")).containsExactly(product.name());
            assertThat(buyerOrders()).as("failed submission creates no order").isEmpty();
            assertThat(db.orderCountOf(buyerId)).isZero();
            assertThat(db.sql().count("SELECT COUNT(*) FROM order_items WHERE order_id IN"
                    + " (SELECT id FROM orders WHERE customer_id = ?)", buyerId)).isZero();
            assertThat(ApiSession.admin().products().getProduct(product.id()).stock()).isEqualTo(3);
            assertThat(db.stockOf(product.sku())).isEqualTo(3);
        } finally {
            BrowserRequestSimulator.restore(driver);
        }

        var orders = checkout.placeOrder();
        long orderId = orders.placedOrderId();

        assertThat(buyerOrders()).extracting(OrderResponse::id).containsExactly(orderId);
        assertThat(db.orderCountOf(buyerId)).isEqualTo(1);
        assertThat(db.orderItems(orderId)).singleElement()
                .satisfies(line -> assertThat(line.get("quantity")).isEqualTo(1));
        assertThat(ApiSession.admin().products().getProduct(product.id()).stock()).isEqualTo(2);
        assertThat(db.stockOf(product.sku())).isEqualTo(2);
    }

    /** An order placed through the UI can be cancelled from its detail dialog. */
    public void adminCancelsAnOrder() {
        // Place a one-line order for a private fixture so cross-layer stock assertions are isolated.
        ShopDatabase db = ShopDatabase.fromConfig();
        ProductData productData = TestDataFactory.newProduct().withStock(1);
        var product = ApiSession.admin().products().createProduct(productData);
        CleanupRegistry.register("deactivate product " + product.id(),
                () -> ApiSession.admin().products().deleteProduct(product.id()));
        ProductPage products = loginAsAdmin().navigation().openProducts().search(product.sku())
                .addToCart(product.name());
        OrderPage orders = products.header().openCart().selectCustomer(buyer().email()).placeOrder();
        long orderId = orders.placedOrderId();

        // Verify that checkout changed the database before cancellation is requested.
        assertThat(db.orderById(orderId))
                .hasValueSatisfying(row -> assertThat(row.get("status")).isEqualTo("PLACED"));
        assertThat(db.orderItems(orderId)).singleElement()
                .satisfies(line -> assertThat(line.get("quantity")).isEqualTo(1));
        assertThat(db.stockOf(product.sku())).isZero();

        // Cancel it from its detail dialog
        orders.cancelOrder(orderId);

        // Confirmed, shown as CANCELLED, and the cancel action is no longer offered
        assertThat(orders.toast().waitForMessage("cancelled")).isEqualTo("Order #" + orderId + " cancelled");
        assertThat(orders.statusOf(orderId)).isEqualTo("CANCELLED");
        assertThat(orders.viewOrder(orderId).hasSubmit()).as("a cancelled order cannot be cancelled again").isFalse();
        assertThat(ApiSession.admin().orders().getOrder(orderId).status()).isEqualTo("CANCELLED");
        assertThat(ApiSession.admin().products().getProduct(product.id()).stock())
                .as("cancellation restores the reserved unit across UI/API/database layers").isEqualTo(1);
        assertThat(db.orderById(orderId))
                .hasValueSatisfying(row -> assertThat(row.get("status")).isEqualTo("CANCELLED"));
        assertThat(db.orderItems(orderId)).singleElement()
                .satisfies(line -> assertThat(line.get("quantity")).isEqualTo(1));
        assertThat(db.stockOf(product.sku())).isEqualTo(1);
    }

    private void assertRejectedCheckoutPreservesState(boolean inactive) {
        ProductData productData = TestDataFactory.newProduct().withStock(1);
        var product = ApiSession.admin().products().createProduct(productData);
        CleanupRegistry.register("deactivate product " + product.id(),
                () -> ApiSession.admin().products().deleteProduct(product.id()));
        ProductPage products = loginAsAdmin().navigation().openProducts();
        products.search(product.sku()).addToCart(product.name());
        var checkout = products.header().openCart().selectCustomer(buyer().email());

        if (inactive) {
            ApiSession.admin().products().deleteProduct(product.id());
        } else {
            ApiSession.admin().products().update(product.id(), Map.of("stock", 0));
        }
        String message = checkout.placeOrderExpectingError();

        assertThat(message).contains(inactive ? "no longer sold" : "0 left");
        assertThat(checkout.cart().column("Product")).containsExactly(product.name());
        assertThat(ApiSession.admin().products().getProduct(product.id()).stock()).isEqualTo(inactive ? 1 : 0);
        assertThat(buyerOrders()).as("rejected checkout leaves no order header or lines").isEmpty();
    }

    private List<OrderResponse> buyerOrders() {
        long customerId = ApiSession.admin().customers().findByEmail(buyer().email()).orElseThrow().id();
        return ApiSession.admin().orders().ordersOf(customerId);
    }
}
