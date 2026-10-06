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
import com.enterprise.automation.tests.base.BaseApiTest;
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

    // Fresh per test (set in @BeforeMethod); safe because tests of one class share one thread
    private CustomerResponse customer;
    private ProductResponse product;
    private ProductResponse otherProduct;

    /** The order service as the admin. */
    private OrderService orders() {
        return admin().orders();
    }

    @BeforeMethod(alwaysRun = true)
    public void givenACustomerAndProducts() {
        // Own products with known price and stock, so every stock assertion is exact
        customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        product = admin().products().createProduct(TestDataFactory.newProduct().withStock(5).withPrice(new BigDecimal("10.50")));
        otherProduct = admin().products().createProduct(TestDataFactory.newProduct().withStock(3).withPrice(new BigDecimal("4.00")));
        // Registered first, so they run last: orders are removed before their customer
        long customerId = customer.id();
        long productId = product.id();
        long otherId = otherProduct.id();
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

    /** Current stock of a product, read fresh through the API. */
    private int stockOf(ProductResponse p) {
        return admin().products().getProduct(p.id()).stock();
    }

    @Test(groups = {"smoke", "sanity"})
    public void placingAnOrderComputesTotalsAndReservesStock() {
        // 2 x product (10.50, stock 5) + 1 x otherProduct (4.00, stock 3)
        Response response = orders().create(OrderRequest.of(customer.id(), item(product.id(), 2), item(otherProduct.id(), 1)));

        expectStatus(response, 201);
        matchesSchema(response, "order");
        OrderResponse order = JsonMapper.fromJson(response.asString(), OrderResponse.class);
        CleanupRegistry.register("delete order " + order.id(), () -> orders().deleteOrder(order.id()));

        assertThat(response.getHeader("Location")).isEqualTo("/api/orders/" + order.id());
        assertThat(order.status()).isEqualTo("PLACED");
        assertThat(order.customerName()).isEqualTo(customer.fullName());
        assertThat(order.total()).isEqualByComparingTo("25.00"); // 2 x 10.50 + 1 x 4.00
        // BigDecimal compared by value (21.0 == 21.00), lines in the order they were sent
        assertThat(order.items()).extracting(OrderResponse.Item::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("21.00"), new BigDecimal("4.00"));
        // Stock is reserved at once: 5 - 2 and 3 - 1
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(stockOf(otherProduct)).isEqualTo(2);
    }

    public void ordersCanBeListedPerCustomerAndStatus() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        // Filter by customer: exactly this order; by customer + SHIPPED: none (it is PLACED)
        assertThat(orders().ordersOf(customer.id())).extracting(OrderResponse::id).containsExactly(order.id());
        List<?> placed = JsonMapper.fromJson(expectStatus(orders().list(
                Map.of("customerId", customer.id(), "status", "SHIPPED")), 200).asString(), List.class);
        assertThat(placed).isEmpty();
    }

    public void statusFollowsTheAllowedFlow() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        // The allowed path: PLACED -> SHIPPED -> DELIVERED
        assertThat(orders().moveTo(order.id(), "SHIPPED").status()).isEqualTo("SHIPPED");
        assertThat(orders().moveTo(order.id(), "DELIVERED").status()).isEqualTo("DELIVERED");

        // A delivered order can no longer be cancelled
        ErrorResponse error = error(expectStatus(orders().changeStatus(order.id(), "CANCELLED"), 409));
        assertThat(error.message()).isEqualTo("Order " + order.id() + " cannot go from DELIVERED to CANCELLED");
    }

    public void cancellingGivesTheStockBack() {
        // Reserve 4 of 5
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 4)));
        assertThat(stockOf(product)).isEqualTo(1);

        orders().moveTo(order.id(), "CANCELLED");

        // All 5 available again
        assertThat(stockOf(product)).isEqualTo(5);
    }

    public void orderingMoreThanTheStockGives409AndChangesNothing() {
        // The first line is fine, the second asks for 6 of 5
        ErrorResponse error = error(expectStatus(orders().create(
                OrderRequest.of(customer.id(), item(otherProduct.id(), 1), item(product.id(), 6))), 409));

        // The message names the product and the numbers; the first line's reservation was rolled back
        assertThat(error.message()).isEqualTo("Insufficient stock for " + product.sku() + ": 5 left, 6 requested");
        assertThat(stockOf(otherProduct)).as("the whole order is rolled back").isEqualTo(3);
        assertThat(orders().ordersOf(customer.id())).isEmpty();
    }

    public void invalidOrdersGive400PerField() {
        // No customer and no items
        ErrorResponse empty = error(expectStatus(orders().create(new OrderRequest(null, List.of())), 400));
        assertThat(empty.fieldErrors()).containsEntry("customerId", "Customer is required")
                .containsEntry("items", "At least one item is required");

        // Quantity 0: the error names the line by index
        ErrorResponse zero = error(expectStatus(orders().create(
                OrderRequest.of(customer.id(), item(product.id(), 0))), 400));
        assertThat(zero.fieldErrors()).containsEntry("items[0].quantity", "Quantity must be at least 1");
    }

    public void unknownReferencesGive404() {
        // Unknown customer, then unknown product
        expectStatus(orders().create(OrderRequest.of(999_999, item(product.id(), 1))), 404);
        expectStatus(orders().create(OrderRequest.of(customer.id(), item(999_999, 1))), 404);
        // Neither attempt reserved anything
        assertThat(stockOf(product)).isEqualTo(5);
    }

    public void inactiveCustomerCannotOrder() {
        // Deactivate this test's customer
        admin().customers().update(customer.id(), Map.of("status", "INACTIVE"));

        // A business rule (409), not a validation error
        ErrorResponse error = error(expectStatus(orders().create(OrderRequest.of(customer.id(), item(product.id(), 1))), 409));

        assertThat(error.message()).isEqualTo("Customer " + customer.id() + " is inactive");
    }

    @Test(groups = "sanity")
    public void viewerCanReadOrdersButNotPlaceThem() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        // Reading an existing order is fine; placing one is admin-only
        expectStatus(viewer().orders().get(order.id()), 200);
        expectStatus(viewer().orders().create(OrderRequest.of(customer.id(), item(product.id(), 1))), 403);
    }
}
