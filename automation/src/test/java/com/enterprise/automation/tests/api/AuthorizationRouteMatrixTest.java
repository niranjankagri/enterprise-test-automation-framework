package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.models.OrderRequest.item;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.models.CustomerRequest;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.LoginRequest;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.models.ProductRequest;
import com.enterprise.automation.api.models.UserRequest;
import com.enterprise.automation.config.Credentials;
import java.math.BigDecimal;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.data.UserData;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.demoapp.http.Router.Access;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.testng.annotations.Test;

/** Exercises every declared protected route with valid requests as anonymous, viewer and admin principals. */
@Test(groups = {"api", "security", "regression"})
public class AuthorizationRouteMatrixTest extends BaseApiTest {

    private enum Principal {
        ANONYMOUS,
        VIEWER,
        ADMIN
    }

    private record RouteCase(String id, String method, String path, Access access, int successStatus, Object body,
            Supplier<Object> deniedWriteState) {
    }

    private record OrderState(Optional<Map<String, Object>> header, List<Map<String, Object>> items,
            long customerOrderCount, int productStock) {
    }

    private record OrderCreateState(long customerOrderCount, int productStock) {
    }

    /** Every route receives valid fixture IDs/payloads so denials prove the declared access check ran. */
    @Test
    public void everyProtectedRouteEnforcesThePrincipalMatrixAndPreservesDeniedWrites() {
        ShopDatabase db = ShopDatabase.fromConfig();

        UserData targetUserData = TestDataFactory.newUser("VIEWER");
        var targetUser = admin().users().createUser(targetUserData);
        registerUserCleanup(targetUser.id());

        CustomerData orderCustomerData = TestDataFactory.newCustomer();
        CustomerResponse orderCustomer = admin().customers().createCustomer(orderCustomerData);
        registerCustomerCleanup(orderCustomer.id());
        CustomerData deletableCustomerData = TestDataFactory.newCustomer();
        CustomerResponse deletableCustomer = admin().customers().createCustomer(deletableCustomerData);
        registerCustomerCleanup(deletableCustomer.id());

        ProductData productData = TestDataFactory.newProduct().withStock(12);
        var product = admin().products().createProduct(productData);
        CleanupRegistry.register("delete product " + product.id(), () -> db.deleteUnusedProduct(product.id()));

        var targetOrder = admin().orders().placeOrder(
                OrderRequest.of(orderCustomer.id(), item(product.id(), 1)));
        registerOrderCleanup(targetOrder.id());

        UserData createdUserData = TestDataFactory.newUser("VIEWER");
        CustomerData createdCustomerData = TestDataFactory.newCustomer();
        ProductData createdProductData = TestDataFactory.newProduct();
        OrderRequest createdOrderRequest = OrderRequest.of(orderCustomer.id(), item(product.id(), 1));

        ProductData currentProductData = productData.withStock(db.stockOf(productData.sku()));
        List<RouteCase> cases = routeCases(db, targetUser.id(), orderCustomer.id(),
                orderCustomerData, deletableCustomer.id(), product.id(), currentProductData, targetOrder.id(),
                createdUserData, createdCustomerData, createdProductData, createdOrderRequest);

        for (RouteCase route : cases) {
            for (Principal principal : Principal.values()) {
                ApiSession session = session(principal, route);
                int expected = expectedStatus(route, principal);
                Object before = isDeniedWrite(route, principal) && route.deniedWriteState() != null
                        ? route.deniedWriteState().get() : null;

                Response response = request(session, route);
                String context = route.id() + " " + principal + " " + route.method() + " " + route.path();
                assertResponseContract(response, expected, route.path(), context);

                if (before != null) {
                    assertThat(route.deniedWriteState().get()).as(context + " must not change persisted state")
                            .isEqualTo(before);
                }
                if (expected < 400) {
                    if (expected == 201) {
                        registerCreatedRecord(route, response, db);
                    }
                    assertSafeUserResponse(route, response);
                    assertStatsResponse(route, response);
                    if ("API-ACL-01".equals(route.id())) {
                        ErrorResponse revoked = error(expectStatus(session.users().me(), 401));
                        assertThat(revoked.message()).isEqualTo("Invalid or expired token");
                    }
                }
            }
        }
    }

    /** User collection and detail contracts must never expose hashes, salts or supplied passwords. */
    @Test
    public void userCollectionAndDetailOmitCredentials() {
        UserData user = TestDataFactory.newUser("VIEWER");
        var created = admin().users().createUser(user);
        registerUserCleanup(created.id());

        Response list = expectStatus(admin().users().list(), 200);
        List<Map<String, Object>> users = list.jsonPath().getList("$");
        assertThat(users).isNotEmpty();
        users.forEach(AuthorizationRouteMatrixTest::assertSafeUserFields);

        Response detail = expectStatus(admin().users().get(created.id()), 200);
        assertSafeUserFields(detail.jsonPath().getMap("$"));
        assertThat(detail.asString()).doesNotContain(user.password(), "passwordHash", "password_hash", "salt");
    }

    private List<RouteCase> routeCases(ShopDatabase db, long userId, long customerId,
            CustomerData customer, long deletableCustomerId, long productId, ProductData product, long orderId,
            UserData newUser, CustomerData newCustomer, ProductData newProduct, OrderRequest newOrder) {
        List<RouteCase> routes = new ArrayList<>();
        routes.add(route("API-ACL-00", "POST", "/auth/login", Access.PUBLIC, 200,
                LoginRequest.of(config().admin()), null));
        routes.add(route("API-ACL-01", "POST", "/auth/logout", Access.USER, 204, Map.of(), null));
        routes.add(route("API-ACL-02", "GET", "/stats", Access.USER, 200, null, null));
        routes.add(route("API-ACL-03", "GET", "/users/me", Access.USER, 200, null, null));
        routes.add(route("API-ACL-04", "GET", "/users", Access.ADMIN, 200, null, null));
        routes.add(route("API-ACL-05", "POST", "/users", Access.ADMIN, 201, UserRequest.from(newUser),
                () -> safeUserByUsername(db, newUser.username())));
        routes.add(route("API-ACL-06", "GET", "/users/" + userId, Access.ADMIN, 200, null, null));
        routes.add(route("API-ACL-07", "PATCH", "/users/" + userId, Access.ADMIN, 200,
                Map.of("fullName", "Authorization Matrix", "role", "VIEWER"), () -> safeUserById(db, userId)));
        routes.add(route("API-ACL-08", "DELETE", "/users/" + userId, Access.ADMIN, 204, null,
                () -> safeUserById(db, userId)));
        routes.add(route("API-ACL-09", "GET", "/customers", Access.USER, 200, null, null));
        routes.add(route("API-ACL-10", "POST", "/customers", Access.ADMIN, 201, CustomerRequest.from(newCustomer),
                () -> db.customerByEmail(newCustomer.email())));
        routes.add(route("API-ACL-11", "GET", "/customers/" + customerId, Access.USER, 200, null, null));
        routes.add(route("API-ACL-12", "PUT", "/customers/" + customerId, Access.ADMIN, 200,
                CustomerRequest.from(customer), () -> db.customerById(customerId)));
        routes.add(route("API-ACL-13", "PATCH", "/customers/" + customerId, Access.ADMIN, 200,
                Map.of("city", "Authorization City"), () -> db.customerById(customerId)));
        routes.add(route("API-ACL-14", "DELETE", "/customers/" + deletableCustomerId, Access.ADMIN, 204, null,
                () -> db.customerById(deletableCustomerId)));
        routes.add(route("API-ACL-15", "GET", "/products", Access.USER, 200, Map.of(), null));
        routes.add(route("API-ACL-16", "POST", "/products", Access.ADMIN, 201, ProductRequest.from(newProduct),
                () -> db.productBySku(newProduct.sku())));
        routes.add(route("API-ACL-17", "GET", "/products/" + productId, Access.USER, 200, null, null));
        routes.add(route("API-ACL-18", "PUT", "/products/" + productId, Access.ADMIN, 200,
                ProductRequest.from(product), () -> db.productBySku(product.sku())));
        routes.add(route("API-ACL-19", "PATCH", "/products/" + productId, Access.ADMIN, 200,
                Map.of("price", new BigDecimal("5.25"), "stock", currentStock(db, product.sku())),
                () -> db.productBySku(product.sku())));
        routes.add(route("API-ACL-21", "GET", "/orders", Access.USER, 200, Map.of(), null));
        routes.add(route("API-ACL-22", "POST", "/orders", Access.ADMIN, 201, newOrder,
                () -> new OrderCreateState(db.orderCountOf(customerId), db.stockOf(product.sku()))));
        routes.add(route("API-ACL-23", "GET", "/orders/" + orderId, Access.USER, 200, null, null));
        routes.add(route("API-ACL-24", "PATCH", "/orders/" + orderId, Access.ADMIN, 200,
                Map.of("status", "SHIPPED"), () -> orderState(db, orderId, customerId, product.sku())));
        routes.add(route("API-ACL-25", "DELETE", "/orders/" + orderId, Access.ADMIN, 204, null,
                () -> orderState(db, orderId, customerId, product.sku())));
        // Deactivate after all order operations so every earlier handler receives an active product.
        routes.add(route("API-ACL-20", "DELETE", "/products/" + productId, Access.ADMIN, 204, null,
                () -> db.productBySku(product.sku())));
        return routes;
    }

    private static RouteCase route(String id, String method, String path, Access access, int successStatus, Object body,
            Supplier<Object> deniedWriteState) {
        return new RouteCase(id, method, path, access, successStatus, body, deniedWriteState);
    }

    private ApiSession session(Principal principal, RouteCase route) {
        if (principal == Principal.ANONYMOUS) {
            return anonymous();
        }
        if ("API-ACL-01".equals(route.id())) {
            Credentials credentials = principal == Principal.ADMIN ? config().admin() : config().viewer();
            return ApiSession.withToken(anonymous().auth().loginAs(credentials).token());
        }
        return principal == Principal.ADMIN ? admin() : viewer();
    }

    private static int expectedStatus(RouteCase route, Principal principal) {
        if (route.access() == Access.PUBLIC) {
            return route.successStatus();
        }
        if (principal == Principal.ANONYMOUS) {
            return 401;
        }
        if (principal == Principal.VIEWER && route.access() == Access.ADMIN) {
            return 403;
        }
        return route.successStatus();
    }

    private static boolean isDeniedWrite(RouteCase route, Principal principal) {
        return principal != Principal.ADMIN && route.access() == Access.ADMIN
                && route.deniedWriteState() != null;
    }

    private static Response request(ApiSession session, RouteCase route) {
        return switch (route.method()) {
            case "GET" -> session.client().get(route.path());
            case "POST" -> session.client().post(route.path(), route.body());
            case "PUT" -> session.client().put(route.path(), route.body());
            case "PATCH" -> session.client().patch(route.path(), route.body());
            case "DELETE" -> session.client().delete(route.path());
            default -> throw new IllegalArgumentException("Unsupported route method " + route.method());
        };
    }

    private static void assertResponseContract(Response response, int expectedStatus, String path, String context) {
        expectStatus(response, expectedStatus);
        if (expectedStatus == 204) {
            assertThat(response.asString()).as(context + " is bodyless").isEmpty();
            return;
        }
        assertThat(response.getContentType()).as(context + " is JSON").startsWith("application/json");
        if (expectedStatus == 401 || expectedStatus == 403) {
            matchesSchema(response, "error");
            ErrorResponse details = error(response);
            assertThat(details.status()).isEqualTo(expectedStatus);
            assertThat(details.path()).isEqualTo("/api" + path);
            assertThat(details.timestamp()).isNotBlank();
        }
    }

    private static void assertSafeUserResponse(RouteCase route, Response response) {
        if ("API-ACL-03".equals(route.id()) || "API-ACL-06".equals(route.id())) {
            assertSafeUserFields(response.jsonPath().getMap("$"));
        } else if ("API-ACL-04".equals(route.id())) {
            List<Map<String, Object>> users = response.jsonPath().getList("$");
            users.forEach(AuthorizationRouteMatrixTest::assertSafeUserFields);
        }
    }

    private static void assertSafeUserFields(Map<String, Object> user) {
        assertThat(user.keySet()).containsExactlyInAnyOrder("id", "username", "fullName", "role", "createdAt");
    }

    private static void assertStatsResponse(RouteCase route, Response response) {
        if ("API-ACL-02".equals(route.id())) {
            assertThat(response.jsonPath().getMap("$").keySet())
                    .containsExactlyInAnyOrder("customers", "products", "orders", "revenue");
        }
    }

    private void registerCreatedRecord(RouteCase route, Response response, ShopDatabase db) {
        long id = response.jsonPath().getLong("id");
        switch (route.id()) {
            case "API-ACL-05" -> registerUserCleanup(id);
            case "API-ACL-10" -> registerCustomerCleanup(id);
            case "API-ACL-16" -> CleanupRegistry.register("delete created product " + id,
                    () -> db.deleteUnusedProduct(id));
            case "API-ACL-22" -> registerOrderCleanup(id);
            default -> {
                // Other allowed operations update or delete the fixture created by this test.
            }
        }
    }

    private static OrderState orderState(ShopDatabase db, long orderId, long customerId, String sku) {
        return new OrderState(db.orderById(orderId), db.orderItems(orderId), db.orderCountOf(customerId), db.stockOf(sku));
    }

    private static Optional<Map<String, Object>> safeUserByUsername(ShopDatabase db, String username) {
        return db.sql().named("authorization user state by username").queryForOne(
                "SELECT id, username, full_name, role, created_at FROM app_users WHERE username = ?", username);
    }

    private static Optional<Map<String, Object>> safeUserById(ShopDatabase db, long id) {
        return db.sql().named("authorization user state by id").queryForOne(
                "SELECT id, username, full_name, role, created_at FROM app_users WHERE id = ?", id);
    }

    private static int currentStock(ShopDatabase db, String sku) {
        return db.stockOf(sku);
    }

    private void registerUserCleanup(long id) {
        CleanupRegistry.register("delete user " + id, () -> {
            Response response = admin().users().delete(id);
            if (response.getStatusCode() != 404) {
                expectStatus(response, 204);
            }
        });
    }

    private void registerCustomerCleanup(long id) {
        CleanupRegistry.register("delete customer " + id, () -> admin().customers().deleteCustomerIfExists(id));
    }

    private void registerOrderCleanup(long id) {
        CleanupRegistry.register("delete order " + id, () -> {
            Response response = admin().orders().delete(id);
            if (response.getStatusCode() != 404) {
                expectStatus(response, 204);
            }
        });
    }

    private static Response expectStatus(Response response, int status) {
        return com.enterprise.automation.api.ApiAssertions.expectStatus(response, status);
    }
}
