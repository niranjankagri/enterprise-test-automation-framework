package com.enterprise.demoapp.http;

import com.enterprise.demoapp.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** One API request: path parameters, query parameters, JSON body and the signed-in user. */
public final class Request {

    // The raw JDK exchange (headers, body stream, method)
    private final HttpExchange exchange;
    // Values of {placeholders} in the route template, e.g. id -> "42"
    private final Map<String, String> pathParams;
    // Decoded query string, e.g. search -> "ava"
    private final Map<String, String> query;
    // Filled in by the router once the bearer token is verified
    private String username;
    private String role;

    Request(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        // Parse the query string once, up front
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    /** First value of a request header, or {@code null}. */
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** Numeric path parameter such as {@code {id}}; 400 if it is not a number. */
    public long pathId(String name) {
        String value = pathParams.get(name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // e.g. GET /api/customers/abc -> a clear 400 instead of a 500
            throw new ApiException(400, "Path parameter '" + name + "' must be a number: " + value);
        }
    }

    /** Trimmed query parameter, or {@code null} when absent or blank. */
    public String query(String name) {
        String value = query.get(name);
        // "?search=" and no search at all mean the same thing: no filter
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The request body as a JSON object; 400 when it is missing, malformed or not an object. */
    public JsonNode body() {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            // Endpoints that need a body must get one
            if (bytes.length == 0) {
                throw new ApiException(400, "Request body is required");
            }
            JsonNode node = Json.MAPPER.readTree(bytes);
            // Arrays, strings or numbers at the top level are not valid request bodies
            if (node == null || !node.isObject()) {
                throw new ApiException(400, "Request body must be a JSON object");
            }
            return node;
        } catch (IOException e) {
            // Jackson parse errors (and stream errors) -> a clear 400, not a 500
            throw new ApiException(400, "Malformed JSON request body");
        }
    }

    /** Called by the authentication filter once the bearer token is verified. */
    public void signIn(String username, String role) {
        this.username = username;
        this.role = role;
    }

    /** Signed-in username, or {@code null} on public routes. */
    public String username() {
        return username;
    }

    /** Signed-in role ({@code ADMIN} or {@code VIEWER}), or {@code null} on public routes. */
    public String role() {
        return role;
    }

    /** Splits {@code a=1&b=two%20words} into a map, URL-decoding keys and values. */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        // No query string at all
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            // "key=value", or just "key" (treated as an empty value)
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
