package com.enterprise.automation.tests.integration;

import static com.enterprise.automation.api.models.OrderRequest.item;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.models.OrderResponse;
import com.enterprise.automation.api.models.ProductResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.DatabaseConnection;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseApiTest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;

/**
 * Verifies that concurrent orders reserve limited inventory atomically and that a rejected order
 * rolls back earlier line reservations.
 */
@Test(groups = {"integration", "db", "regression"})
public class OrderReservationConcurrencyTest extends BaseApiTest {

    private static final int INITIAL_SHARED_STOCK = 3;
    private static final int SHARED_QUANTITY = 2;
    private static final Duration BLOCKER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(20);

    private record ApiCallResult(Response response, RuntimeException failure) {
    }

    /** Two requests cannot both reserve the last units or leave a partial reservation behind. */
    @Test
    public void competingOrdersReserveLimitedStockWithoutOverselling() throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        ShopDatabase db = ShopDatabase.fromConfig();
        CustomerResponse customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        CleanupRegistry.register("delete customer " + customer.id(),
                () -> admin().customers().deleteCustomerIfExists(customer.id()));
        ProductResponse firstOnly = givenProduct(db, 10);
        ProductResponse secondOnly = givenProduct(db, 10);
        ProductResponse shared = givenProduct(db, INITIAL_SHARED_STOCK);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ApiCallResult> firstCall = null;
        Future<ApiCallResult> secondCall = null;
        int blockedReservations = 0;
        boolean bothRequestsReady = false;

        try (Connection lock = DatabaseConnection.fromConfig().open()) {
            lock.setAutoCommit(false);
            holdProductRow(lock, shared.id());

            firstCall = executor.submit(() -> createOrder(customer.id(), firstOnly.id(), shared.id(), ready, start));
            secondCall = executor.submit(() -> createOrder(customer.id(), secondOnly.id(), shared.id(), ready, start));
            bothRequestsReady = ready.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            start.countDown();

            // Both handlers must be waiting at the shared stock UPDATE before the row lock is released.
            blockedReservations = awaitBlockedReservations();
            lock.commit();

            ApiCallResult first = firstCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ApiCallResult second = secondCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            registerSuccessfulOrder(first);
            registerSuccessfulOrder(second);

            assertThat(bothRequestsReady).as("both request workers reached the start gate").isTrue();
            assertThat(blockedReservations).as("both reservations reached the shared product row").isEqualTo(2);
            assertThat(first.failure()).as("first order request").isNull();
            assertThat(second.failure()).as("second order request").isNull();
            assertThat(new int[] {first.response().statusCode(), second.response().statusCode()})
                    .as("one order reserves stock and the competing order is rejected")
                    .containsExactlyInAnyOrder(201, 409);

            boolean firstWon = first.response().statusCode() == 201;
            assertThat(db.stockOf(shared.sku())).as("shared inventory cannot become negative or oversold")
                    .isEqualTo(INITIAL_SHARED_STOCK - SHARED_QUANTITY);
            assertThat(db.stockOf(firstOnly.sku())).as("the winning order keeps its first-line reservation")
                    .isEqualTo(firstWon ? 9 : 10);
            assertThat(db.stockOf(secondOnly.sku())).as("the losing order rolls back its first-line reservation")
                    .isEqualTo(firstWon ? 10 : 9);
            assertThat(db.orderCountOf(customer.id())).as("a rejected order leaves no order header").isEqualTo(1);

            Response winningResponse = firstWon ? first.response() : second.response();
            OrderResponse order = JsonMapper.fromJson(winningResponse.asString(), OrderResponse.class);
            assertThat(order.items()).extracting(OrderResponse.Item::productId)
                    .containsExactly(firstWon ? firstOnly.id() : secondOnly.id(), shared.id());
        } finally {
            if (firstCall != null) {
                start.countDown();
            }
            executor.shutdownNow();
            executor.awaitTermination(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private ProductResponse givenProduct(ShopDatabase db, int stock) {
        ProductResponse product = admin().products().createProduct(
                TestDataFactory.newProduct().withStock(stock).withPrice(new BigDecimal("1.00")));
        CleanupRegistry.register("delete unused product " + product.id(), () -> db.deleteUnusedProduct(product.id()));
        return product;
    }

    private ApiCallResult createOrder(long customerId, long privateProductId, long sharedProductId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            return new ApiCallResult(null, new IllegalStateException("Order request start gate was not released"));
        }
        try {
            Response response = admin().orders().create(
                    OrderRequest.of(customerId, item(privateProductId, 1), item(sharedProductId, SHARED_QUANTITY)));
            return new ApiCallResult(response, null);
        } catch (RuntimeException e) {
            return new ApiCallResult(null, e);
        }
    }

    private void holdProductRow(Connection lock, long productId) throws SQLException {
        try (PreparedStatement statement = lock.prepareStatement("UPDATE products SET stock = stock WHERE id = ?")) {
            statement.setLong(1, productId);
            assertThat(statement.executeUpdate()).as("test lock targets one product row").isEqualTo(1);
        }
    }

    private int awaitBlockedReservations() throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL"
                + " AND UPPER(EXECUTING_STATEMENT) LIKE 'UPDATE PRODUCTS SET STOCK%'";
        long deadline = System.nanoTime() + BLOCKER_TIMEOUT.toNanos();
        try (Connection observer = DatabaseConnection.fromConfig().open();
                PreparedStatement statement = observer.prepareStatement(sql)) {
            int blocked;
            do {
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    blocked = result.getInt(1);
                }
                if (blocked >= 2 || System.nanoTime() >= deadline) {
                    return blocked;
                }
                Thread.yield();
            } while (true);
        }
    }

    private void registerSuccessfulOrder(ApiCallResult result) {
        if (result.response() != null && result.response().statusCode() == 201) {
            OrderResponse order = JsonMapper.fromJson(result.response().asString(), OrderResponse.class);
            CleanupRegistry.register("delete order " + order.id(), () -> admin().orders().deleteOrder(order.id()));
        }
    }
}
