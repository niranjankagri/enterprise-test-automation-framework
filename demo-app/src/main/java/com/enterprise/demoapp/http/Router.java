package com.enterprise.demoapp.http;

import com.enterprise.demoapp.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal REST router for the JDK HTTP server.
 *
 * <p>Routes are declared as {@code GET /api/customers/{id}} with an access level. Every response
 * is JSON and carries an {@code X-Request-Id} header; every error has the same shape:
 * {@code {status, error, message, path, timestamp, fieldErrors}}.
 */
public final class Router implements HttpHandler {

    /** Who may call a route. */
    public enum Access {
        /** No token needed (login). */
        PUBLIC,
        /** Any signed-in user. */
        USER,
        /** Signed-in user with the ADMIN role. */
        ADMIN
    }

    /** Verifies a bearer token and signs the request in; returns {@code false} for an invalid token. */
    public interface Authenticator {
        boolean authenticate(String token, Request request);
    }

    private static final Logger LOG = LoggerFactory.getLogger(Router.class);
    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");

    private final List<Route> routes = new ArrayList<>();
    private final Authenticator authenticator;
    private final long latencyMillis;

    public Router(Authenticator authenticator, long latencyMillis) {
        this.authenticator = authenticator;
        this.latencyMillis = latencyMillis;
    }

    public Router route(String method, String path, Access access, Function<Request, Response> handler) {
        routes.add(new Route(method, path, access, handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        String path = exchange.getRequestURI().getPath();
        try {
            simulateLatency();
            Response response = dispatch(exchange, path);
            send(exchange, response.status(), response.body(), response.headers());
        } catch (ApiException e) {
            send(exchange, e.status(), error(e.status(), e.getMessage(), path, e.fieldErrors()), Map.of());
        } catch (RuntimeException e) {
            LOG.error("Request {} {} failed [{}]", exchange.getRequestMethod(), path, requestId, e);
            send(exchange, 500, error(500, "Internal server error", path, Map.of()), Map.of());
        } finally {
            exchange.close();
        }
    }

    private Response dispatch(HttpExchange exchange, String path) {
        boolean pathMatched = false;
        for (Route route : routes) {
            Map<String, String> params = route.match(path);
            if (params == null) {
                continue;
            }
            pathMatched = true;
            if (!route.method.equalsIgnoreCase(exchange.getRequestMethod())) {
                continue;
            }
            Request request = new Request(exchange, params);
            authorize(route.access, request);
            return route.handler.apply(request);
        }
        throw pathMatched
                ? new ApiException(405, "Method " + exchange.getRequestMethod() + " is not allowed on " + path)
                : new ApiException(404, "No endpoint " + path);
    }

    private void authorize(Access access, Request request) {
        if (access == Access.PUBLIC) {
            return;
        }
        String header = request.header("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(401, "Missing bearer token");
        }
        if (!authenticator.authenticate(header.substring(7).trim(), request)) {
            throw new ApiException(401, "Invalid or expired token");
        }
        if (access == Access.ADMIN && !"ADMIN".equals(request.role())) {
            throw new ApiException(403, "This action needs the ADMIN role");
        }
    }

    private void simulateLatency() {
        if (latencyMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMillis); // the application imitates a real backend; tests must wait, not sleep
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> error(int status, String message, String path, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", reason(status));
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", Instant.now().toString());
        if (!fields.isEmpty()) {
            body.put("fieldErrors", fields);
        }
        return body;
    }

    private static String reason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            default -> "Internal Server Error";
        };
    }

    private static void send(HttpExchange exchange, int status, Object body, Map<String, String> headers)
            throws IOException {
        headers.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A route: method + path template compiled to a regex. */
    private static final class Route {
        private final String method;
        private final Access access;
        private final Function<Request, Response> handler;
        private final Pattern pattern;
        private final List<String> names = new ArrayList<>();

        Route(String method, String template, Access access, Function<Request, Response> handler) {
            this.method = method;
            this.access = access;
            this.handler = handler;
            Matcher m = PARAM.matcher(template);
            StringBuilder regex = new StringBuilder();
            int last = 0;
            while (m.find()) {
                regex.append(Pattern.quote(template.substring(last, m.start()))).append("([^/]+)");
                names.add(m.group(1));
                last = m.end();
            }
            regex.append(Pattern.quote(template.substring(last)));
            this.pattern = Pattern.compile(regex + "/?");
        }

        Map<String, String> match(String path) {
            Matcher m = pattern.matcher(path);
            if (!m.matches()) {
                return null;
            }
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < names.size(); i++) {
                params.put(names.get(i), m.group(i + 1));
            }
            return params;
        }
    }
}
