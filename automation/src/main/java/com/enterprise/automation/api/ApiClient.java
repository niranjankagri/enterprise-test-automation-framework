package com.enterprise.automation.api;

import static io.restassured.RestAssured.given;

import com.enterprise.automation.config.ConfigManager;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.Map;

/**
 * The HTTP layer every service uses: base URL, JSON, bearer token, logging.
 *
 * <p>Immutable: {@link #withToken(String)} returns a new client. Each call builds its request
 * from scratch (no {@code RestAssured.baseURI}-style global state), so clients can be shared by
 * parallel tests safely.
 */
public final class ApiClient {

    // Stateless filters, shared by all clients and threads
    private static final ApiLoggingFilter LOGGING = new ApiLoggingFilter();
    private static final ReportingApiFilter REPORTING = new ReportingApiFilter();

    // e.g. http://localhost:8080/api; request paths are appended to it
    private final URI baseUri;
    // Bearer token, or null for anonymous calls
    private final String token;

    private ApiClient(URI baseUri, String token) {
        this.baseUri = baseUri;
        this.token = token;
    }

    /** Anonymous client for the configured API base URL. */
    public static ApiClient anonymous() {
        return new ApiClient(ConfigManager.config().apiBaseUrl(), null);
    }

    /** Same base URL, authenticated with {@code bearerToken}. */
    public ApiClient withToken(String bearerToken) {
        return new ApiClient(baseUri, bearerToken);
    }

    // One method per HTTP verb; bodies are any object (serialized to JSON) or a ready JSON string

    public Response get(String path) {
        return request().get(path);
    }

    /** GET with query parameters (URL-encoded by REST Assured). */
    public Response get(String path, Map<String, ?> queryParams) {
        return request().queryParams(queryParams).get(path);
    }

    public Response post(String path, Object body) {
        return withBody(body).post(path);
    }

    public Response put(String path, Object body) {
        return withBody(body).put(path);
    }

    public Response patch(String path, Object body) {
        return withBody(body).patch(path);
    }

    public Response delete(String path) {
        return request().delete(path);
    }

    /** For negative tests: send a raw body exactly as given (malformed JSON, wrong types...). */
    public Response postRaw(String path, String rawBody) {
        return request().body(rawBody).post(path);
    }

    /** A request with a JSON body: strings are sent as they are, objects through the framework's mapper. */
    private RequestSpecification withBody(Object body) {
        return request().body(body instanceof String s ? s : JsonMapper.toJson(body));
    }

    /** A fresh request specification for every call (given() creates a new one each time). */
    private RequestSpecification request() {
        RequestSpecification spec = given()
                .baseUri(baseUri.toString())
                // Send and expect JSON
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                // Report step + attachments first, then the log line (the report filter wraps the logging one)
                .filter(REPORTING)
                .filter(LOGGING);
        // Authenticated clients add the bearer token
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        return spec;
    }
}
