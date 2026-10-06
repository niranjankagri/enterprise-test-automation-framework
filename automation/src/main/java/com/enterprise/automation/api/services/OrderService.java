package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiAssertions;
import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.models.OrderResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/** {@code /api/orders}. */
public final class OrderService extends BaseService {

    private static final String PATH = "/orders";

    public OrderService(ApiClient client) {
        super(client);
    }

    // ---- raw calls ----

    /** {@code GET /orders} with filters: customerId, status. */
    public Response list(Map<String, ?> query) {
        return client.get(PATH, query);
    }

    /** {@code GET /orders/{id}}. */
    public Response get(long id) {
        return client.get(PATH + "/" + id);
    }

    /** {@code POST /orders}: place an order. */
    public Response create(Object body) {
        return client.post(PATH, body);
    }

    /** {@code PATCH /orders/{id}} with {@code {status}}: move the order along its status flow. */
    public Response changeStatus(long id, String status) {
        return client.patch(PATH + "/" + id, Map.of("status", status));
    }

    /** {@code DELETE /orders/{id}}. */
    public Response delete(long id) {
        return client.delete(PATH + "/" + id);
    }

    // ---- typed happy paths ----

    /** Places the order, expects 201. */
    public OrderResponse placeOrder(OrderRequest request) {
        return expect(create(request), 201, OrderResponse.class);
    }

    /** Reads one order, expects 200. */
    public OrderResponse getOrder(long id) {
        return expect(get(id), 200, OrderResponse.class);
    }

    /** All orders of a customer, newest first. */
    public List<OrderResponse> ordersOf(long customerId) {
        return expect(list(Map.of("customerId", customerId)), 200, new TypeReference<List<OrderResponse>>() { });
    }

    /** Changes the status, expects 200 (use changeStatus to test forbidden transitions). */
    public OrderResponse moveTo(long id, String status) {
        return expect(changeStatus(id, status), 200, OrderResponse.class);
    }

    /** Removes the order; a PLACED order gives its stock back. */
    public void deleteOrder(long id) {
        ApiAssertions.expectStatus(delete(id), 204);
    }
}
