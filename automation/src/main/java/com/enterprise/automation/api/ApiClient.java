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

    private static final ApiLoggingFilter LOGGING = new ApiLoggingFilter();

    private final URI baseUri;
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

    public Response get(String path) {
        return request().get(path);
    }

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

    private RequestSpecification withBody(Object body) {
        return request().body(body instanceof String s ? s : JsonMapper.toJson(body));
    }

    private RequestSpecification request() {
        RequestSpecification spec = given()
                .baseUri(baseUri.toString())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .filter(LOGGING);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        return spec;
    }
}
