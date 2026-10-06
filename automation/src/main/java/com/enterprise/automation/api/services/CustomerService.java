package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiAssertions;
import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.models.CustomerRequest;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.data.CustomerData;
import com.fasterxml.jackson.core.type.TypeReference;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code /api/customers}. */
public final class CustomerService extends BaseService {

    private static final String PATH = "/customers";

    public CustomerService(ApiClient client) {
        super(client);
    }

    // ---- raw calls ----

    /** {@code GET /customers[?search=]}; {@code null} lists everyone. */
    public Response list(String search) {
        return search == null ? client.get(PATH) : client.get(PATH, Map.of("search", search));
    }

    /** {@code GET /customers/{id}}. */
    public Response get(long id) {
        return client.get(PATH + "/" + id);
    }

    /** {@code POST /customers}; the body can be a CustomerRequest, a Map (negative tests) or JSON text. */
    public Response create(Object body) {
        return client.post(PATH, body);
    }

    /** {@code PUT /customers/{id}}: full replacement. */
    public Response replace(long id, Object body) {
        return client.put(PATH + "/" + id, body);
    }

    /** {@code PATCH /customers/{id}}: only the given fields change. */
    public Response update(long id, Map<String, ?> changes) {
        return client.patch(PATH + "/" + id, changes);
    }

    /** {@code DELETE /customers/{id}}. */
    public Response delete(long id) {
        return client.delete(PATH + "/" + id);
    }

    // ---- typed happy paths ----

    /** Creates the customer, expects 201, returns what the API stored. */
    public CustomerResponse createCustomer(CustomerData customer) {
        return expect(create(CustomerRequest.from(customer)), 201, CustomerResponse.class);
    }

    /** Reads one customer, expects 200. */
    public CustomerResponse getCustomer(long id) {
        return expect(get(id), 200, CustomerResponse.class);
    }

    /** Searches, expects 200, returns the matches. */
    public List<CustomerResponse> findCustomers(String search) {
        // Anonymous subclass of TypeReference keeps the generic type (List<CustomerResponse>) at runtime
        return expect(list(search), 200, new TypeReference<List<CustomerResponse>>() { });
    }

    /** The customer with exactly this email, if any. */
    public Optional<CustomerResponse> findByEmail(String email) {
        // The search is a "contains" match; keep only the exact email
        return findCustomers(email).stream().filter(c -> c.email().equalsIgnoreCase(email)).findFirst();
    }

    /** Deletes, expects 204. */
    public void deleteCustomer(long id) {
        ApiAssertions.expectStatus(delete(id), 204);
    }

    /** Idempotent delete for clean-ups: "already gone" (404) is fine too. */
    public void deleteCustomerIfExists(long id) {
        Response response = delete(id);
        if (response.getStatusCode() != 404) {
            ApiAssertions.expectStatus(response, 204);
        }
    }
}
