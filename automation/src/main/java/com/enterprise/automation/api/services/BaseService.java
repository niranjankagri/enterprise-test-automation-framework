package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiAssertions;
import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.restassured.response.Response;

/**
 * Base of the API services. Each service offers two kinds of methods:
 * <ul>
 *   <li>raw calls returning the {@link Response} ({@code create}, {@code get}...), for tests that
 *       check status codes, headers and error bodies, including negative cases;</li>
 *   <li>typed happy paths ({@code createCustomer}, {@code getCustomer}...) that check the expected
 *       status and return a model, for tests (and clean-ups) that just need the data.</li>
 * </ul>
 */
abstract class BaseService {

    // The session's client (base URL, token, filters)
    protected final ApiClient client;

    protected BaseService(ApiClient client) {
        this.client = client;
    }

    /** Checks {@code status} and maps the body to {@code type}. */
    protected static <T> T expect(Response response, int status, Class<T> type) {
        // Wrong status -> AssertionError with the (masked) body, before any mapping is attempted
        ApiAssertions.expectStatus(response, status);
        return JsonMapper.fromJson(response.asString(), type);
    }

    /** Same for generic types such as lists. */
    protected static <T> T expect(Response response, int status, TypeReference<T> type) {
        ApiAssertions.expectStatus(response, status);
        return JsonMapper.fromJson(response.asString(), type);
    }
}
