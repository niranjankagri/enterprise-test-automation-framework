package com.enterprise.automation.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.demoapp.Json;
import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.auth.AuthService;
import com.enterprise.demoapp.http.Response;
import com.enterprise.demoapp.http.Router;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.testng.annotations.Test;

/** Verifies bearer-token expiry at its exact deadline using an adjustable clock. */
@Test(groups = {"api", "regression"})
public class AuthServiceExpiryTest {

    private static final String USERNAME = "expiry-user";
    private static final String PASSWORD = "Synthetic-Password-1";

    /** A token is usable before expiry and rejected at the configured deadline. */
    @Test
    public void tokenExpiresAtItsDeadline() throws Exception {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        Duration tokenLifetime = Duration.ofMinutes(5);
        String databaseName = "auth_expiry_" + UUID.randomUUID().toString().replace("-", "");

        try (Database database = new Database(databaseName, 0, false)) {
            AuthService auth = new AuthService(database, clock, tokenLifetime);
            try (Connection connection = database.connect()) {
                auth.createUser(connection, USERNAME, PASSWORD, "Expiry User", "VIEWER");
            }

            Router router = new Router(auth::authenticate, 0)
                    .route("POST", "/api/auth/login", Router.Access.PUBLIC, auth::login)
                    .route("GET", "/api/protected", Router.Access.USER,
                            request -> Response.ok(Map.of("username", request.username())));
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                server.createContext("/api", router);
                server.setExecutor(executor);
                server.start();

                String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> login = client.send(HttpRequest.newBuilder(
                                URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(
                                Map.of("username", USERNAME, "password", PASSWORD)), StandardCharsets.UTF_8))
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                assertThat(login.statusCode()).isEqualTo(200);
                String token = Json.MAPPER.readTree(login.body()).path("token").asText();
                assertThat(token).isNotBlank();

                assertThat(protectedRequest(client, baseUrl, token).statusCode()).isEqualTo(200);

                clock.advance(tokenLifetime);

                assertThat(protectedRequest(client, baseUrl, token).statusCode()).isEqualTo(401);
            } finally {
                server.stop(0);
                executor.shutdownNow();
            }
        }
    }

    private static HttpResponse<String> protectedRequest(HttpClient client, String baseUrl, String token)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/protected"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final class AdjustableClock extends Clock {

        private volatile Instant current;
        private final ZoneId zone;

        private AdjustableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new AdjustableClock(current, newZone);
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
