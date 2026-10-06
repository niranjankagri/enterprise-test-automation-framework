package com.enterprise.demoapp.http;

import java.util.Map;

/**
 * What a route returns: status, JSON body (or {@code null}) and extra headers.
 *
 * @param status  HTTP status code
 * @param body    object serialized as JSON, or {@code null} for an empty body
 * @param headers additional response headers, e.g. {@code Location}
 */
public record Response(int status, Object body, Map<String, String> headers) {

    public static Response ok(Object body) {
        return new Response(200, body, Map.of());
    }

    public static Response created(Object body, String location) {
        return new Response(201, body, Map.of("Location", location));
    }

    public static Response noContent() {
        return new Response(204, null, Map.of());
    }
}
