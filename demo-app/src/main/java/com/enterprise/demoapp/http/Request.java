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

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private String username;
    private String role;

    Request(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** Numeric path parameter such as {@code {id}}; 400 if it is not a number. */
    public long pathId(String name) {
        String value = pathParams.get(name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Path parameter '" + name + "' must be a number: " + value);
        }
    }

    /** Trimmed query parameter, or {@code null} when absent or blank. */
    public String query(String name) {
        String value = query.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The request body as a JSON object; 400 when it is missing, malformed or not an object. */
    public JsonNode body() {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                throw new ApiException(400, "Request body is required");
            }
            JsonNode node = Json.MAPPER.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new ApiException(400, "Request body must be a JSON object");
            }
            return node;
        } catch (IOException e) {
            throw new ApiException(400, "Malformed JSON request body");
        }
    }

    /** Called by the authentication filter once the bearer token is verified. */
    public void signIn(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public String username() {
        return username;
    }

    public String role() {
        return role;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
