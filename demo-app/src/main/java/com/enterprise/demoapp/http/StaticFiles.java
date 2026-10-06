package com.enterprise.demoapp.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Serves the web UI from the classpath folder {@code /static}; {@code /} goes to the login page. */
public final class StaticFiles implements HttpHandler {

    // Content types of the file types the UI uses; anything else is sent as binary
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // try-with-resources closes the exchange in every branch
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            // The site root redirects to the login page
            if ("/".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/login.html");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            // Refuse path traversal (../) so nothing outside /static can be read
            if (path.contains("..")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            // Files are packaged in the jar under /static
            try (InputStream in = StaticFiles.class.getResourceAsStream("/static" + path)) {
                // Unknown file: plain-text 404
                if (in == null) {
                    byte[] body = "Not found".getBytes();
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }
                byte[] body = in.readAllBytes();
                // Content type from the file extension
                String extension = path.substring(path.lastIndexOf('.') + 1);
                exchange.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
                // Never cached: every test run must get the current UI
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }
}
