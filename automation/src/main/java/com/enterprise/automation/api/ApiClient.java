package com.enterprise.automation.api;

import static io.restassured.RestAssured.given;

import com.enterprise.automation.config.ConfigManager;
import io.restassured.http.ContentType;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.HttpClientConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * The HTTP layer every service uses: base URL, JSON, bearer token, logging.
 *
 * <p>Immutable: {@link #withToken(String)} returns a new client. Each call builds its request
 * from scratch (no {@code RestAssured.baseURI}-style global state), so clients can be shared by
 * parallel tests safely.
 */
public final class ApiClient {

    /** Header carrying the correlation id of every call. */
    public static final String REQUEST_ID = "X-Request-Id";

    // Stateless filters, shared by all clients and threads
    private static final ApiLoggingFilter LOGGING = new ApiLoggingFilter();
    private static final ReportingApiFilter REPORTING = new ReportingApiFilter();

    // e.g. http://localhost:8080/api; request paths are appended to it
    private final URI baseUri;
    // Bearer token, or null for anonymous calls
    private final String token;
    // Fixed correlation id, or null for a new one per call (the normal case)
    private final String requestId;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    /** Use anonymous() or a signed-in session; every client keeps one request id for the calls of one test. */
    private ApiClient(URI baseUri, String token, String requestId, int connectTimeoutMillis, int readTimeoutMillis) {
        this.baseUri = baseUri;
        this.token = token;
        this.requestId = requestId;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /** Anonymous client for the configured API base URL. */
    public static ApiClient anonymous() {
        return forEndpoint(ConfigManager.config().apiBaseUrl(), ConfigManager.config().apiConnectTimeoutMillis(),
                ConfigManager.config().apiReadTimeoutMillis());
    }

    /** An isolated endpoint/transport configuration, useful for controlled service fixtures. */
    public static ApiClient forEndpoint(URI endpoint, int connectMillis, int readMillis) {
        if (endpoint == null || endpoint.getHost() == null
                || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || connectMillis <= 0 || readMillis <= 0) {
            throw new IllegalArgumentException("HTTP endpoint and positive transport timeouts are required");
        }
        return new ApiClient(endpoint, null, null, connectMillis, readMillis);
    }

    /** Same base URL, authenticated with {@code bearerToken}. */
    public ApiClient withToken(String bearerToken) {
        return new ApiClient(baseUri, bearerToken, requestId, connectTimeoutMillis, readTimeoutMillis);
    }

    /** Same client, sending {@code id} as the correlation id of every call (to trace a call by a known id). */
    public ApiClient withRequestId(String id) {
        return new ApiClient(baseUri, token, id, connectTimeoutMillis, readTimeoutMillis);
    }

    // One method per HTTP verb; bodies are any object (serialized to JSON) or a ready JSON string

    public Response get(String path) {
        return request().get(path);
    }

    /** GET with query parameters (URL-encoded by REST Assured). */
    public Response get(String path, Map<String, ?> queryParams) {
        return request().queryParams(queryParams).get(path);
    }

    /** POST with the body serialised as JSON. */
    public Response post(String path, Object body) {
        return withBody(body).post(path);
    }

    /** PUT with the body serialised as JSON. */
    public Response put(String path, Object body) {
        return withBody(body).put(path);
    }

    /** PATCH with the body serialised as JSON. */
    public Response patch(String path, Object body) {
        return withBody(body).patch(path);
    }

    /** DELETE without a body. */
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
                .config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", connectTimeoutMillis)
                        .setParam("http.socket.timeout", readTimeoutMillis)
                        .setParam("http.connection-manager.timeout", (long) connectTimeoutMillis)))
                // Send and expect JSON
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                // Correlation id: the application logs and returns it, our log and report show it, so one
                // request can be followed from test to server even when the call fails without a response
                .header(REQUEST_ID, requestId != null ? requestId : UUID.randomUUID().toString())
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
