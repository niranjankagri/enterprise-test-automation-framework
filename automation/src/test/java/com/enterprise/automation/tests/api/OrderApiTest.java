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

    private CustomerResponse customer;
    private ProductResponse product;
    private ProductResponse otherProduct;

    private OrderService orders() {
        return admin().orders();
    }

    @BeforeMethod(alwaysRun = true)
    public void givenACustomerAndProducts() {
        customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        product = admin().products().createProduct(TestDataFactory.newProduct().withStock(5).withPrice(new BigDecimal("10.50")));
        otherProduct = admin().products().createProduct(TestDataFactory.newProduct().withStock(3).withPrice(new BigDecimal("4.00")));
        // Registered first, so they run last: orders are removed before their customer
        long customerId = customer.id();
        long productId = product.id();
        long otherId = otherProduct.id();
        CleanupRegistry.register("delete customer " + customerId, () -> admin().customers().deleteCustomer(customerId));
        CleanupRegistry.register("deactivate product " + productId, () -> admin().products().deleteProduct(productId));
        CleanupRegistry.register("deactivate product " + otherId, () -> admin().products().deleteProduct(otherId));
    }

    private OrderResponse placeOrder(OrderRequest request) {
        OrderResponse order = orders().placeOrder(request);
        CleanupRegistry.register("delete order " + order.id(), () -> orders().deleteOrder(order.id()));
        return order;
    }

    private int stockOf(ProductResponse p) {
        return admin().products().getProduct(p.id()).stock();
    }

    @Test(groups = {"smoke", "sanity"})
    public void placingAnOrderComputesTotalsAndReservesStock() {
        Response response = orders().create(OrderRequest.of(customer.id(), item(product.id(), 2), item(otherProduct.id(), 1)));

        expectStatus(response, 201);
        matchesSchema(response, "order");
        OrderResponse order = JsonMapper.fromJson(response.asString(), OrderResponse.class);
        CleanupRegistry.register("delete order " + order.id(), () -> orders().deleteOrder(order.id()));

        assertThat(response.getHeader("Location")).isEqualTo("/api/orders/" + order.id());
        assertThat(order.status()).isEqualTo("PLACED");
        assertThat(order.customerName()).isEqualTo(customer.fullName());
        assertThat(order.total()).isEqualByComparingTo("25.00"); // 2 x 10.50 + 1 x 4.00
        assertThat(order.items()).extracting(OrderResponse.Item::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("21.00"), new BigDecimal("4.00"));
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(stockOf(otherProduct)).isEqualTo(2);
    }

    public void ordersCanBeListedPerCustomerAndStatus() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        assertThat(orders().ordersOf(customer.id())).extracting(OrderResponse::id).containsExactly(order.id());
        List<?> placed = JsonMapper.fromJson(expectStatus(orders().list(
                Map.of("customerId", customer.id(), "status", "SHIPPED")), 200).asString(), List.class);
        assertThat(placed).isEmpty();
    }

    public void statusFollowsTheAllowedFlow() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        assertThat(orders().moveTo(order.id(), "SHIPPED").status()).isEqualTo("SHIPPED");
        assertThat(orders().moveTo(order.id(), "DELIVERED").status()).isEqualTo("DELIVERED");

        ErrorResponse error = error(expectStatus(orders().changeStatus(order.id(), "CANCELLED"), 409));
        assertThat(error.message()).isEqualTo("Order " + order.id() + " cannot go from DELIVERED to CANCELLED");
    }

    public void cancellingGivesTheStockBack() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 4)));
        assertThat(stockOf(product)).isEqualTo(1);

        orders().moveTo(order.id(), "CANCELLED");

        assertThat(stockOf(product)).isEqualTo(5);
    }

    public void orderingMoreThanTheStockGives409AndChangesNothing() {
        ErrorResponse error = error(expectStatus(orders().create(
                OrderRequest.of(customer.id(), item(otherProduct.id(), 1), item(product.id(), 6))), 409));

        assertThat(error.message()).isEqualTo("Insufficient stock for " + product.sku() + ": 5 left, 6 requested");
        assertThat(stockOf(otherProduct)).as("the whole order is rolled back").isEqualTo(3);
        assertThat(orders().ordersOf(customer.id())).isEmpty();
    }

    public void invalidOrdersGive400PerField() {
        ErrorResponse empty = error(expectStatus(orders().create(new OrderRequest(null, List.of())), 400));
        assertThat(empty.fieldErrors()).containsEntry("customerId", "Customer is required")
                .containsEntry("items", "At least one item is required");

        ErrorResponse zero = error(expectStatus(orders().create(
                OrderRequest.of(customer.id(), item(product.id(), 0))), 400));
        assertThat(zero.fieldErrors()).containsEntry("items[0].quantity", "Quantity must be at least 1");
    }

    public void unknownReferencesGive404() {
        expectStatus(orders().create(OrderRequest.of(999_999, item(product.id(), 1))), 404);
        expectStatus(orders().create(OrderRequest.of(customer.id(), item(999_999, 1))), 404);
        assertThat(stockOf(product)).isEqualTo(5);
    }

    public void inactiveCustomerCannotOrder() {
        admin().customers().update(customer.id(), Map.of("status", "INACTIVE"));

        ErrorResponse error = error(expectStatus(orders().create(OrderRequest.of(customer.id(), item(product.id(), 1))), 409));

        assertThat(error.message()).isEqualTo("Customer " + customer.id() + " is inactive");
    }

    @Test(groups = "sanity")
    public void viewerCanReadOrdersButNotPlaceThem() {
        OrderResponse order = placeOrder(OrderRequest.of(customer.id(), item(product.id(), 1)));

        expectStatus(viewer().orders().get(order.id()), 200);
        expectStatus(viewer().orders().create(OrderRequest.of(customer.id(), item(product.id(), 1))), 403);
    }
}
