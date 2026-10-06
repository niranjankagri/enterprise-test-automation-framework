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
 * is JSON and carries an {@code X-Request-Id} header (the client's, if it sent one); every error has the same shape:
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
        // Implemented by AuthService.authenticate (passed in as a method reference)
        boolean authenticate(String token, Request request);
    }

    // What a client-supplied X-Request-Id may look like
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    // Logger for unexpected server errors (500s)
    private static final Logger LOG = LoggerFactory.getLogger(Router.class);
    // Finds "{name}" placeholders in a route template such as /api/customers/{id}
    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");

    // All registered routes, checked in registration order
    private final List<Route> routes = new ArrayList<>();
    // Checks bearer tokens for every non-public route
    private final Authenticator authenticator;
    // Artificial delay per API call, so the UI behaves like a real (slower) backend
    private final long latencyMillis;

    public Router(Authenticator authenticator, long latencyMillis) {
        // Keep the token checker and the latency setting for every request
        this.authenticator = authenticator;
        this.latencyMillis = latencyMillis;
    }

    /** Registers a route; returns {@code this} so routes can be declared as one fluent chain. */
    public Router route(String method, String path, Access access, Function<Request, Response> handler) {
        // Compile the template once, at start-up, not on every request
        routes.add(new Route(method, path, access, handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // An id per request: returned as a header and written to the log, so a client-side failure
        // can be matched with the server-side log line. A client may send its own (correlation id).
        String requestId = requestId(exchange.getRequestHeaders().getFirst("X-Request-Id"));
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        // Only the path is used for routing; the query string is read later by Request
        String path = exchange.getRequestURI().getPath();
        try {
            // Imitate network/backend latency before doing the real work
            simulateLatency();
            // Find the route, check access, run the handler
            Response response = dispatch(exchange, path);
            // Write status, headers and JSON body
            send(exchange, response.status(), response.body(), response.headers());
        } catch (ApiException e) {
            // Expected errors (400/401/403/404/405/409) become the standard error body
            send(exchange, e.status(), error(e.status(), e.getMessage(), path, e.fieldErrors()), Map.of());
        } catch (RuntimeException e) {
            // Anything else is a bug in the app: log it with the request id, answer a generic 500
            LOG.error("Request {} {} failed [{}]", exchange.getRequestMethod(), path, requestId, e);
            send(exchange, 500, error(500, "Internal server error", path, Map.of()), Map.of());
        } finally {
            // Always release the connection, whatever happened
            exchange.close();
        }
    }

    /** The client's id if it is a safe token (letters, digits, dashes; at most 64), otherwise a new UUID. */
    private static String requestId(String fromClient) {
        // Never echo arbitrary text into headers and logs
        return fromClient != null && SAFE_ID.matcher(fromClient).matches() ? fromClient : UUID.randomUUID().toString();
    }

    /** Finds the route for {@code path} and the request method, checks access and runs it. */
    private Response dispatch(HttpExchange exchange, String path) {
        // Remembers whether any route matched the path, to tell 404 (no such path) from 405 (wrong method)
        boolean pathMatched = false;
        for (Route route : routes) {
            // Path parameters ({id} -> "42"), or null when this route's path does not match
            Map<String, String> params = route.match(path);
            if (params == null) {
                continue;
            }
            pathMatched = true;
            // Same path, different method (e.g. PUT on a GET-only path): keep looking
            if (!route.method.equalsIgnoreCase(exchange.getRequestMethod())) {
                continue;
            }
            // Wrap the exchange so the handler gets typed access to params, query and body
            Request request = new Request(exchange, params);
            // 401/403 are thrown here, before the handler runs
            authorize(route.access, request);
            // Run the business logic of the endpoint
            return route.handler.apply(request);
        }
        // No route served the request: wrong method on a known path, or an unknown path
        throw pathMatched
                ? new ApiException(405, "Method " + exchange.getRequestMethod() + " is not allowed on " + path)
                : new ApiException(404, "No endpoint " + path);
    }

    /** Enforces the route's access level: 401 without a valid token, 403 without the needed role. */
    private void authorize(Access access, Request request) {
        // Login itself is public
        if (access == Access.PUBLIC) {
            return;
        }
        // Expect "Authorization: Bearer <token>" (scheme compared case-insensitively)
        String header = request.header("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(401, "Missing bearer token");
        }
        // Unknown, logged-out or expired token
        if (!authenticator.authenticate(header.substring(7).trim(), request)) {
            throw new ApiException(401, "Invalid or expired token");
        }
        // Signed in, but a VIEWER calling an ADMIN-only route
        if (access == Access.ADMIN && !"ADMIN".equals(request.role())) {
            throw new ApiException(403, "This action needs the ADMIN role");
        }
    }

    /** Waits {@code latencyMillis} before answering (0 disables it). */
    private void simulateLatency() {
        if (latencyMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMillis); // the application imitates a real backend; tests must wait, not sleep
        } catch (InterruptedException e) {
            // Keep the interrupt flag so the server can shut down cleanly
            Thread.currentThread().interrupt();
        }
    }

    /** Builds the single error body shape used by every error response. */
    private static Map<String, Object> error(int status, String message, String path, Map<String, String> fields) {
        // LinkedHashMap keeps the field order stable in the JSON (nicer to read, easier to compare)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", reason(status));
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", Instant.now().toString());
        // Field errors only for validation failures; omitted otherwise
        if (!fields.isEmpty()) {
            body.put("fieldErrors", fields);
        }
        return body;
    }

    /** Standard HTTP reason phrase for the status codes this API uses. */
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

    /** Writes status, extra headers and (if any) the JSON body. */
    private static void send(HttpExchange exchange, int status, Object body, Map<String, String> headers)
            throws IOException {
        // Extra headers from the handler, e.g. Location for 201 Created
        headers.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        // No body (204 No Content): -1 tells the JDK server there is no response body
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        // Serialize first, so the exact Content-Length is known before sending headers
        byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        // try-with-resources closes the body stream, which completes the response
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A route: method + path template compiled to a regex. */
    private static final class Route {
        // HTTP method, e.g. GET
        private final String method;
        // Who may call it
        private final Access access;
        // The endpoint's business logic
        private final Function<Request, Response> handler;
        // The template as a regex, e.g. /api/customers/([^/]+)/?
        private final Pattern pattern;
        // Placeholder names in template order, e.g. [id]
        private final List<String> names = new ArrayList<>();

        Route(String method, String template, Access access, Function<Request, Response> handler) {
            this.method = method;
            this.access = access;
            this.handler = handler;
            // Turn "/api/customers/{id}" into a regex: literal parts quoted, each {name} -> one path segment
            Matcher m = PARAM.matcher(template);
            StringBuilder regex = new StringBuilder();
            // End of the previous placeholder: where the next literal part starts
            int last = 0;
            while (m.find()) {
                // Literal text before the placeholder, then a group matching one segment (no "/")
                regex.append(Pattern.quote(template.substring(last, m.start()))).append("([^/]+)");
                // Remember the placeholder name for this group
                names.add(m.group(1));
                last = m.end();
            }
            // Literal text after the last placeholder
            regex.append(Pattern.quote(template.substring(last)));
            // Accept an optional trailing slash
            this.pattern = Pattern.compile(regex + "/?");
        }

        /** Path parameters if {@code path} matches this route, otherwise {@code null}. */
        Map<String, String> match(String path) {
            Matcher m = pattern.matcher(path);
            // The whole path must match, not just a prefix
            if (!m.matches()) {
                return null;
            }
            // Pair each placeholder name with its captured value
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < names.size(); i++) {
                params.put(names.get(i), m.group(i + 1));
            }
            return params;
        }
    }
}
