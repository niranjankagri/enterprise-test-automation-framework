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
    public void tc312_competingOrdersReserveLimitedStockWithoutOverselling() throws SQLException, InterruptedException,
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
            blockedReservations = awaitBlockedReservations(2);
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

    /** Opposite input line orders acquire overlapping product locks consistently and finish without deadlock. */
    @Test
    public void tc313_overlappingOrdersWithReversedLinesCompleteWithoutDeadlock() throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        ShopDatabase db = ShopDatabase.fromConfig();
        CustomerResponse customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        CleanupRegistry.register("delete customer " + customer.id(),
                () -> admin().customers().deleteCustomerIfExists(customer.id()));
        ProductResponse lowerIdProduct = givenProduct(db, 10);
        ProductResponse higherIdProduct = givenProduct(db, 10);

        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch startFirst = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch startSecond = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ApiCallResult> lowerFirstCall = null;
        Future<ApiCallResult> higherFirstCall = null;

        try (Connection lock = DatabaseConnection.fromConfig().open()) {
            lock.setAutoCommit(false);
            holdProductRow(lock, lowerIdProduct.id());

            lowerFirstCall = executor.submit(() -> createOrderInOrder(customer.id(), lowerIdProduct.id(),
                    higherIdProduct.id(), firstReady, startFirst));
            assertThat(firstReady.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            startFirst.countDown();
            assertThat(awaitBlockedReservations(1)).as("first order waits on the held lowest-ID product").isEqualTo(1);

            higherFirstCall = executor.submit(() -> createOrderInOrder(customer.id(), higherIdProduct.id(),
                    lowerIdProduct.id(), secondReady, startSecond));
            assertThat(secondReady.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            startSecond.countDown();
            int blockedReservations = awaitBlockedReservations(2);
            lock.commit();

            ApiCallResult lowerFirst = lowerFirstCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ApiCallResult higherFirst = higherFirstCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            registerSuccessfulOrder(lowerFirst);
            registerSuccessfulOrder(higherFirst);

            assertThat(blockedReservations).as("both transactions contend on the same first lock").isEqualTo(2);
            assertThat(lowerFirst.failure()).as("lower-ID-first order").isNull();
            assertThat(higherFirst.failure()).as("higher-ID-first order").isNull();
            assertThat(new int[] {lowerFirst.response().statusCode(), higherFirst.response().statusCode()})
                    .containsExactly(201, 201);
            assertThat(db.stockOf(lowerIdProduct.sku())).isEqualTo(8);
            assertThat(db.stockOf(higherIdProduct.sku())).isEqualTo(8);
            assertThat(db.orderCountOf(customer.id())).isEqualTo(2);
            OrderResponse lowerFirstOrder = JsonMapper.fromJson(lowerFirst.response().asString(), OrderResponse.class);
            OrderResponse higherFirstOrder = JsonMapper.fromJson(higherFirst.response().asString(), OrderResponse.class);
            assertThat(db.orderItems(lowerFirstOrder.id())).hasSize(2);
            assertThat(db.orderItems(higherFirstOrder.id())).hasSize(2);
        } finally {
            startFirst.countDown();
            startSecond.countDown();
            executor.shutdownNow();
            executor.awaitTermination(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Cancellation restores products in stable ID order when two orders contain the same products in reverse order. */
    @Test
    public void tc314_overlappingCancellationsWithReversedLinesCompleteWithoutDeadlock() throws SQLException,
            InterruptedException, ExecutionException, TimeoutException {
        ShopDatabase db = ShopDatabase.fromConfig();
        CustomerResponse customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        CleanupRegistry.register("delete customer " + customer.id(),
                () -> admin().customers().deleteCustomerIfExists(customer.id()));
        ProductResponse lowerIdProduct = givenProduct(db, 10);
        ProductResponse higherIdProduct = givenProduct(db, 10);
        OrderResponse lowerFirstOrder = admin().orders().placeOrder(OrderRequest.of(customer.id(),
                item(lowerIdProduct.id(), 1), item(higherIdProduct.id(), 1)));
        OrderResponse higherFirstOrder = admin().orders().placeOrder(OrderRequest.of(customer.id(),
                item(higherIdProduct.id(), 1), item(lowerIdProduct.id(), 1)));
        registerOrderCleanup(lowerFirstOrder.id());
        registerOrderCleanup(higherFirstOrder.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ApiCallResult> firstCall = null;
        Future<ApiCallResult> secondCall = null;
        int blockedTransitions = 0;

        try (Connection lock = DatabaseConnection.fromConfig().open()) {
            lock.setAutoCommit(false);
            holdProductRow(lock, lowerIdProduct.id());
            holdProductRow(lock, higherIdProduct.id());
            firstCall = executor.submit(() -> releaseOrder(lowerFirstOrder.id(), false, ready, start));
            secondCall = executor.submit(() -> releaseOrder(higherFirstOrder.id(), false, ready, start));
            assertThat(ready.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            start.countDown();
            blockedTransitions = awaitBlockedOrderTransitions();
            lock.commit();

            ApiCallResult first = firstCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ApiCallResult second = secondCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(blockedTransitions).as("both cancellations reached a held product row").isEqualTo(2);
            assertThat(first.failure()).isNull();
            assertThat(second.failure()).isNull();
            assertThat(new int[] {first.response().statusCode(), second.response().statusCode()})
                    .containsExactly(200, 200);
            assertThat(db.stockOf(lowerIdProduct.sku())).isEqualTo(10);
            assertThat(db.stockOf(higherIdProduct.sku())).isEqualTo(10);
            assertThat(admin().orders().getOrder(lowerFirstOrder.id()).status()).isEqualTo("CANCELLED");
            assertThat(admin().orders().getOrder(higherFirstOrder.id()).status()).isEqualTo("CANCELLED");
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Two simultaneous cancellations must restore the reservation only once. */
    @Test
    public void tc315_concurrentCancellationRestoresStockExactlyOnce() throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        assertConcurrentRelease(false, false);
    }

    /** Two simultaneous deletes must not both return the same order's reserved stock. */
    @Test
    public void tc316_concurrentDeletionRestoresStockExactlyOnce() throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        assertConcurrentRelease(true, true);
    }

    /** A cancellation racing a deletion still returns the reservation exactly once. */
    @Test
    public void tc317_concurrentCancellationAndDeletionRestoresStockExactlyOnce() throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        assertConcurrentRelease(false, true);
    }

    private void assertConcurrentRelease(boolean firstDelete, boolean secondDelete) throws SQLException, InterruptedException,
            ExecutionException, TimeoutException {
        ShopDatabase db = ShopDatabase.fromConfig();
        CustomerResponse customer = admin().customers().createCustomer(TestDataFactory.newCustomer());
        CleanupRegistry.register("delete customer " + customer.id(),
                () -> admin().customers().deleteCustomerIfExists(customer.id()));
        ProductResponse product = givenProduct(db, 10);
        OrderResponse order = admin().orders().placeOrder(OrderRequest.of(customer.id(), item(product.id(), 2)));
        CleanupRegistry.register("delete order " + order.id(), () -> {
            Response existing = admin().orders().get(order.id());
            if (existing.getStatusCode() == 404) {
                return;
            }
            assertThat(existing.getStatusCode()).isEqualTo(200);
            admin().orders().deleteOrder(order.id());
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ApiCallResult> firstCall = null;
        Future<ApiCallResult> secondCall = null;
        int blockedTransitions = 0;

        try (Connection lock = DatabaseConnection.fromConfig().open()) {
            lock.setAutoCommit(false);
            holdProductRow(lock, product.id());

            firstCall = executor.submit(() -> releaseOrder(order.id(), firstDelete, ready, start));
            secondCall = executor.submit(() -> releaseOrder(order.id(), secondDelete, ready, start));
            boolean bothRequestsReady = ready.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            start.countDown();

            // Both requests must be waiting in the same order's transition, one may wait on its row lock.
            blockedTransitions = awaitBlockedOrderTransitions();
            lock.commit();

            ApiCallResult first = firstCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ApiCallResult second = secondCall.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(bothRequestsReady).as("both order transition workers reached the start gate").isTrue();
            assertThat(blockedTransitions).as("both requests contended on the order or product row").isEqualTo(2);
            assertThat(first.failure()).as("first order transition request").isNull();
            assertThat(second.failure()).as("second order transition request").isNull();

            assertThat(db.stockOf(product.sku())).as("a reservation must be restored exactly once").isEqualTo(10);
            if (firstDelete == secondDelete) {
                assertThat(new int[] {first.response().statusCode(), second.response().statusCode()})
                        .as("one transition succeeds; the stale competing request sees the committed state")
                        .containsExactlyInAnyOrder(firstDelete ? new int[] {204, 404} : new int[] {200, 409});
            } else {
                int cancellationStatus = firstDelete ? second.response().statusCode() : first.response().statusCode();
                int deletionStatus = firstDelete ? first.response().statusCode() : second.response().statusCode();
                assertThat(cancellationStatus).isIn(200, 404);
                assertThat(deletionStatus).isEqualTo(204);
            }
            if (firstDelete || secondDelete) {
                assertThat(admin().orders().get(order.id()).getStatusCode()).isEqualTo(404);
            } else {
                assertThat(admin().orders().getOrder(order.id()).status()).isEqualTo("CANCELLED");
            }
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

    private ApiCallResult createOrderInOrder(long customerId, long firstProductId, long secondProductId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            return new ApiCallResult(null, new IllegalStateException("Order request start gate was not released"));
        }
        try {
            Response response = admin().orders().create(
                    OrderRequest.of(customerId, item(firstProductId, 1), item(secondProductId, 1)));
            return new ApiCallResult(response, null);
        } catch (RuntimeException e) {
            return new ApiCallResult(null, e);
        }
    }

    private ApiCallResult releaseOrder(long orderId, boolean delete, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            return new ApiCallResult(null, new IllegalStateException("Order transition start gate was not released"));
        }
        try {
            Response response = delete ? admin().orders().delete(orderId)
                    : admin().orders().changeStatus(orderId, "CANCELLED");
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

    private int awaitBlockedReservations(int expected) throws SQLException {
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
                if (blocked >= expected || System.nanoTime() >= deadline) {
                    return blocked;
                }
                Thread.yield();
            } while (true);
        }
    }

    private int awaitBlockedOrderTransitions() throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL"
                + " AND (UPPER(EXECUTING_STATEMENT) LIKE 'UPDATE PRODUCTS SET STOCK%'"
                + " OR UPPER(EXECUTING_STATEMENT) LIKE 'SELECT STATUS FROM ORDERS%')";
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

    private void registerOrderCleanup(long orderId) {
        CleanupRegistry.register("delete order " + orderId, () -> {
            Response existing = admin().orders().get(orderId);
            if (existing.getStatusCode() == 404) {
                return;
            }
            assertThat(existing.getStatusCode()).isEqualTo(200);
            admin().orders().deleteOrder(orderId);
        });
    }
}
