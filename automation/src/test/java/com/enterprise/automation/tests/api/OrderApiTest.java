package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static com.enterprise.automation.api.models.OrderRequest.item;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.api.models.ProductResponse;
import com.enterprise.automation.api.services.OrderService;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * {@code /api/orders}: totals, stock reservation and the status flow. Each test gets its own
 * customer and its own products with a known stock, so stock assertions are exact and no other
 * test can change them.
 */
@Test(groups = {"api", "regression"})
public class OrderApiTest extends BaseApiTest {

    /** What each test starts with: its own customer and two products with known stock. */
    private record Fixture(CustomerResponse customer, ProductResponse product, ProductResponse otherProduct) {
    }

    // Fresh per test (set in @BeforeMethod), per thread: TestNG runs a @BeforeMethod on its test's
    // thread, so this also holds with parallel=methods, where tests of this class run at the same time
    private static final ThreadLocal<Fixture> FIXTURE = new ThreadLocal<>();

    private static CustomerResponse customer() {
        return FIXTURE.get().customer();
    }

    private static ProductResponse product() {
        return FIXTURE.get().product();
    }

    private static ProductResponse otherProduct() {
        return FIXTURE.get().otherProduct();
    }

    /** The order service as the admin. */
    private OrderService orders() {
        return admin().orders();
    }

    /**
     * Every test gets its own customer and two products with known price and stock, cleaned up afterwards in the
     * right order.
     */
    @BeforeMethod(alwaysRun = true)
    public void givenACustomerAndProducts() {
        // Own products with known price and stock, so every stock assertion is exact
        FIXTURE.set(new Fixture(
                admin().customers().createCustomer(TestDataFactory.newCustomer()),
                admin().products().createProduct(TestDataFactory.newProduct().withStock(5).withPrice(new BigDecimal("10.50"))),
                admin().products().createProduct(TestDataFactory.newProduct().withStock(3).withPrice(new BigDecimal("4.00")))));
        // Registered first, so they run last: orders are removed before their customer
        long customerId = customer().id();
        long productId = product().id();
        long otherId = otherProduct().id();
        CleanupRegistry.register("delete customer " + customerId, () -> admin().customers().deleteCustomerIfExists(customerId));
        CleanupRegistry.register("deactivate product " + productId, () -> admin().products().deleteProduct(productId));
        CleanupRegistry.register("deactivate product " + otherId, () -> admin().products().deleteProduct(otherId));
    }

    /** Places an order (expects 201) and registers its removal. */
    private OrderResponse placeOrder(OrderRequest request) {
        OrderResponse order = orders().placeOrder(request);
        CleanupRegistry.register("delete order " + order.id(), () -> orders().deleteOrder(order.id()));
        return order;
    }

    /** Rejected-order assertion that registers cleanup if a regression unexpectedly creates an order. */
    private ErrorResponse expectInvalidOrder(String body) {
        Response response = orders().create(body);
        if (response.statusCode() == 201) {
            OrderResponse unexpectedOrder = JsonMapper.fromJson(response.asString(), OrderResponse.class);
            CleanupRegistry.register("delete unexpected order " + unexpectedOrder.id(),
                    () -> orders().deleteOrder(unexpectedOrder.id()));
        }
        return error(expectStatus(response, 400));
    }

    /** Current stock of a product, read fresh through the API. */
    private int stockOf(ProductResponse p) {
        return admin().products().getProduct(p.id()).stock();
    }

    /** Placing an order returns 201 with its Location, computes line and order totals, and reserves the stock. */
    @Test(groups = {"smoke", "sanity"})
    public void tc229_placingAnOrderComputesTotalsAndReservesStock() {
        // 2 x product (10.50, stock 5) + 1 x otherProduct (4.00, stock 3)
        Response response = orders().create(OrderRequest.of(customer().id(), item(product().id(), 2), item(otherProduct().id(), 1)));

        expectStatus(response, 201);
        matchesSchema(response, "order");
        OrderResponse order = JsonMapper.fromJson(response.asString(), OrderResponse.class);
        CleanupRegistry.register("delete order " + order.id(), () -> orders().deleteOrder(order.id()));

        assertThat(response.getHeader("Location")).isEqualTo("/api/orders/" + order.id());
        assertThat(order.status()).isEqualTo("PLACED");
        assertThat(order.customerName()).isEqualTo(customer().fullName());
        assertThat(order.total()).isEqualByComparingTo("25.00"); // 2 x 10.50 + 1 x 4.00
        // BigDecimal compared by value (21.0 == 21.00), lines in the order they were sent
        assertThat(order.items()).extracting(OrderResponse.Item::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("21.00"), new BigDecimal("4.00"));
        // Stock is reserved at once: 5 - 2 and 3 - 1
        assertThat(stockOf(product())).isEqualTo(3);
        assertThat(stockOf(otherProduct())).isEqualTo(2);
    }

    /** Duplicate product lines remain separate ordered lines and collectively reserve exact stock once. */
    public void tc230_duplicateProductLinesRemainSeparateAndCanConsumeExactStock() {
        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(product().id(), 2),
                item(product().id(), 3)));

        assertThat(order.items()).extracting(OrderResponse.Item::quantity).containsExactly(2, 3);
        assertThat(order.items()).extracting(OrderResponse.Item::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("21.00"), new BigDecimal("31.50"));
        assertThat(order.total()).isEqualByComparingTo("52.50");
        assertThat(stockOf(product())).as("both lines reserve all available units").isZero();
    }

    /** Orders can be filtered by customer and by status. */
    public void tc231_ordersCanBeListedPerCustomerAndStatus() {
        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(product().id(), 1)));

        // Filter by customer: exactly this order; by customer + SHIPPED: none (it is PLACED)
        assertThat(orders().ordersOf(customer().id())).extracting(OrderResponse::id).containsExactly(order.id());
        List<?> placed = JsonMapper.fromJson(expectStatus(orders().list(
                Map.of("customerId", customer().id(), "status", "SHIPPED")), 200).asString(), List.class);
        assertThat(placed).isEmpty();
    }

    /** Status moves PLACED -> SHIPPED -> DELIVERED; a delivered order cannot be cancelled (409). */
    public void tc232_statusFollowsTheAllowedFlow() {
        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(product().id(), 1)));

        // The allowed path: PLACED -> SHIPPED -> DELIVERED
        assertThat(orders().moveTo(order.id(), "SHIPPED").status()).isEqualTo("SHIPPED");
        assertThat(orders().moveTo(order.id(), "DELIVERED").status()).isEqualTo("DELIVERED");

        // A delivered order can no longer be cancelled
        ErrorResponse error = error(expectStatus(orders().changeStatus(order.id(), "CANCELLED"), 409));
        assertThat(error.message()).isEqualTo("Order " + order.id() + " cannot go from DELIVERED to CANCELLED");
    }

    /** Cancelling an order puts its units back in stock. */
    public void tc233_cancellingGivesTheStockBack() {
        // Reserve 4 of 5
        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(product().id(), 4)));
        assertThat(stockOf(product())).isEqualTo(1);

        orders().moveTo(order.id(), "CANCELLED");

        // All 5 available again
        assertThat(stockOf(product())).isEqualTo(5);
    }

    /** Ordering more than the stock is a 409 naming the product, and the whole order is rolled back. */
    public void tc234_orderingMoreThanTheStockGives409AndChangesNothing() {
        // The first line is fine, the second asks for 6 of 5
        ErrorResponse error = error(expectStatus(orders().create(
                OrderRequest.of(customer().id(), item(otherProduct().id(), 1), item(product().id(), 6))), 409));

        // The message names the product and the numbers; the first line's reservation was rolled back
        assertThat(error.message()).isEqualTo("Insufficient stock for " + product().sku() + ": 5 left, 6 requested");
        assertThat(stockOf(otherProduct())).as("the whole order is rolled back").isEqualTo(3);
        assertThat(orders().ordersOf(customer().id())).isEmpty();
    }

    /** Missing customer, no items and a zero quantity each give a field error; a line is named by its index. */
    public void tc235_invalidOrdersGive400PerField() {
        // No customer and no items
        ErrorResponse empty = error(expectStatus(orders().create(new OrderRequest(null, List.of())), 400));
        assertThat(empty.fieldErrors()).containsEntry("customerId", "Customer is required")
                .containsEntry("items", "At least one item is required");

        // Quantity 0: the error names the line by index
        ErrorResponse zero = error(expectStatus(orders().create(
                OrderRequest.of(customer().id(), item(product().id(), 0))), 400));
        assertThat(zero.fieldErrors()).containsEntry("items[0].quantity",
                "Quantity must be a whole number between 1 and 2147483647");
    }

    /** Invalid JSON quantity shapes and out-of-range integers are rejected without changing state. */
    @Test(dataProvider = "invalidQuantities", dataProviderClass = TestDataProviders.class)
    public void tc236_invalidQuantitiesAreRejectedWithoutChangingState(String caseName, String quantityJson) {
        String quantityField = quantityJson == null ? "" : ",\"quantity\":" + quantityJson;
        String item = "{\"productId\":" + product().id() + quantityField + "}";
        String body = "{\"customerId\":" + customer().id() + ",\"items\":[" + item + "]}";

        ErrorResponse error = expectInvalidOrder(body);

        assertThat(error.fieldErrors()).as(caseName).containsEntry("items[0].quantity",
                "Quantity must be a whole number between 1 and 2147483647");
        assertThat(stockOf(product())).as(caseName + " leaves inventory unchanged").isEqualTo(5);
        assertThat(orders().ordersOf(customer().id())).as(caseName + " creates no order").isEmpty();
    }

    /** The largest supported positive quantity parses, then fails only the stock business rule. */
    public void tc237_maximumSupportedQuantityIsAStockConflict() {
        ErrorResponse error = error(expectStatus(orders().create(OrderRequest.of(
                customer().id(), item(product().id(), Integer.MAX_VALUE))), 409));

        assertThat(error.message()).contains("5 left, 2147483647 requested");
        assertThat(stockOf(product())).isEqualTo(5);
        assertThat(orders().ordersOf(customer().id())).isEmpty();
    }

    /** A valid earlier line is not reserved when a later line has an overflowing quantity. */
    public void tc238_overflowingQuantityInMixedOrderRollsBackEveryLine() {
        String body = "{\"customerId\":" + customer().id() + ",\"items\":["
                + "{\"productId\":" + otherProduct().id() + ",\"quantity\":1},"
                + "{\"productId\":" + product().id() + ",\"quantity\":4294967297}]}";

        ErrorResponse error = expectInvalidOrder(body);

        assertThat(error.fieldErrors()).containsKey("items[1].quantity");
        assertThat(stockOf(otherProduct())).as("the valid first line is rolled back").isEqualTo(3);
        assertThat(stockOf(product())).isEqualTo(5);
        assertThat(orders().ordersOf(customer().id())).isEmpty();
    }

    /** An unknown customer or product is 404 and reserves nothing. */
    public void tc239_unknownReferencesGive404() {
        // Unknown customer, then unknown product
        expectStatus(orders().create(OrderRequest.of(999_999, item(product().id(), 1))), 404);
        expectStatus(orders().create(OrderRequest.of(customer().id(), item(999_999, 1))), 404);
        // Neither attempt reserved anything
        assertThat(stockOf(product())).isEqualTo(5);
    }

    /** An inactive customer cannot order: a business rule (409), not a validation error. */
    public void tc240_inactiveCustomerCannotOrder() {
        // Deactivate this test's customer
        admin().customers().update(customer().id(), Map.of("status", "INACTIVE"));

        // A business rule (409), not a validation error
        ErrorResponse error = error(expectStatus(orders().create(OrderRequest.of(customer().id(), item(product().id(), 1))), 409));

        assertThat(error.message()).isEqualTo("Customer " + customer().id() + " is inactive");
    }

    /** A viewer can read an order but not place one (403). */
    @Test(groups = "sanity")
    public void tc241_viewerCanReadOrdersButNotPlaceThem() {
        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(product().id(), 1)));

        // Reading an existing order is fine; placing one is admin-only
        expectStatus(viewer().orders().get(order.id()), 200);
        expectStatus(viewer().orders().create(OrderRequest.of(customer().id(), item(product().id(), 1))), 403);
    }

    /** The exact DECIMAL(12,2) maximum order total is accepted and persisted precisely. */
    @Test(groups = "integration")
    public void tc242_maximumSupportedOrderTotalIsStoredExactly() {
        ShopDatabase database = ShopDatabase.fromConfig();
        ProductResponse highPrice = admin().products().createProduct(TestDataFactory.newProduct()
                .withPrice(new BigDecimal("99999999.99")).withStock(100));
        CleanupRegistry.register("remove high-price boundary product " + highPrice.sku(),
                () -> database.deleteUnusedProduct(highPrice.id()));
        ProductResponse remainder = admin().products().createProduct(TestDataFactory.newProduct()
                .withPrice(new BigDecimal("0.99")).withStock(1));
        CleanupRegistry.register("remove remainder boundary product " + remainder.sku(),
                () -> database.deleteUnusedProduct(remainder.id()));

        OrderResponse order = placeOrder(OrderRequest.of(customer().id(), item(highPrice.id(), 100),
                item(remainder.id(), 1)));

        assertThat(order.total()).isEqualByComparingTo("9999999999.99");
        assertThat(database.orderById(order.id()).orElseThrow()).containsEntry("total", new BigDecimal("9999999999.99"));
        assertThat(database.stockOf(highPrice.sku())).isZero();
        assertThat(database.stockOf(remainder.sku())).isZero();
    }

    /** A total beyond DECIMAL(12,2) is a client error and leaves no order, lines or stock changes. */
    @Test(groups = "integration")
    public void tc243_orderTotalAboveDatabaseMaximumIsRejectedWithoutWriting() {
        ShopDatabase database = ShopDatabase.fromConfig();
        ProductResponse highPrice = admin().products().createProduct(TestDataFactory.newProduct()
                .withPrice(new BigDecimal("99999999.99")).withStock(101));
        CleanupRegistry.register("remove high-price overflow product " + highPrice.sku(),
                () -> database.deleteUnusedProduct(highPrice.id()));

        Response response = orders().create(OrderRequest.of(customer().id(), item(highPrice.id(), 101)));

        assertThat(response.statusCode()).as("unsupported order totals are rejected as client input").isEqualTo(400);
        ErrorResponse error = error(response);
        assertThat(error.fieldErrors()).containsEntry("items", "Order total must be at most 9999999999.99");
        assertThat(database.orderCountOf(customer().id())).as("no order header was committed").isZero();
        assertThat(database.sql().count("SELECT COUNT(*) FROM order_items WHERE product_id = ?", highPrice.id()))
                .as("no order line was committed").isZero();
        assertThat(database.stockOf(highPrice.sku())).as("the failed transaction leaves stock unchanged").isEqualTo(101);
    }
    /** A product ID must not wrap to a real product during long conversion. */
    public void tc244_overflowingProductIdsAreRejectedWithoutStateChanges() {
        String oversized = java.math.BigInteger.ONE.shiftLeft(64)
                .add(java.math.BigInteger.valueOf(product().id())).toString();
        String body = "{\"customerId\":" + customer().id() + ",\"items\":[{\"productId\":"
                + oversized + ",\"quantity\":1}]}";
        ErrorResponse failure = expectInvalidOrder(body);
        assertThat(failure.fieldErrors()).containsKey("items[0].productId");
        assertThat(stockOf(product())).isEqualTo(5);
        assertThat(orders().ordersOf(customer().id())).isEmpty();
    }

    public void tc245_orderItemLimitIsRejectedBeforeWrites() {
        String line = "{\"productId\":" + product().id() + ",\"quantity\":1}";
        String body = "{\"customerId\":" + customer().id() + ",\"items\":["
                + String.join(",", java.util.Collections.nCopies(1001, line)) + "]}";
        assertThat(expectInvalidOrder(body).fieldErrors()).containsKey("items");
        assertThat(stockOf(product())).isEqualTo(5);
        assertThat(orders().ordersOf(customer().id())).isEmpty();
    }

}
