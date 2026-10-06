package com.enterprise.automation.api;

import com.enterprise.automation.api.models.ErrorResponse;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

/**
 * Checks shared by services and tests. Failure messages always include the response body (with
 * secrets masked), so a failed check explains itself without re-running the test.
 */
public final class ApiAssertions {

    private ApiAssertions() {
    }

    /** Fails unless the status is {@code expected}. */
    public static Response expectStatus(Response response, int expected) {
        // Returns the response, so calls can be chained: error(expectStatus(response, 404))
        if (response.getStatusCode() != expected) {
            throw new AssertionError("Expected HTTP " + expected + " but got " + response.getStatusCode()
                    + ". Body: " + SecretMasker.mask(response.asString()));
        }
        return response;
    }

    /** Fails unless the body matches {@code schemas/<name>.json} on the classpath. */
    public static Response matchesSchema(Response response, String name) {
        // REST Assured's validator reports every violated rule (missing field, wrong type, extra field)
        response.then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/" + name + ".json"));
        return response;
    }

    /** The body as the API's error shape (after checking its schema). */
    public static ErrorResponse error(Response response) {
        // Every error must have the one agreed shape before its fields are trusted
        matchesSchema(response, "error");
        return JsonMapper.fromJson(response.asString(), ErrorResponse.class);
    }
}
