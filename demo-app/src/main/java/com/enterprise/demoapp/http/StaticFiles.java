package com.enterprise.demoapp.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Serves the web UI from the classpath folder {@code /static}; {@code /} goes to the login page. */
public final class StaticFiles implements HttpHandler {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/login.html");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            if (path.contains("..")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            try (InputStream in = StaticFiles.class.getResourceAsStream("/static" + path)) {
                if (in == null) {
                    byte[] body = "Not found".getBytes();
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }
                byte[] body = in.readAllBytes();
                String extension = path.substring(path.lastIndexOf('.') + 1);
                exchange.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }
}
