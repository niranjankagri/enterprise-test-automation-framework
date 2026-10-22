package com.enterprise.automation.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.api.ApiClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/** A stalled controlled service is bounded by the client read timeout, not the workflow timeout. */
@Test(groups = {"api", "regression"})
public class ApiTransportTimeoutTest {
    public void stalledServiceHitsConfiguredReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService handlers = Executors.newCachedThreadPool();
        ExecutorService callers = Executors.newSingleThreadExecutor();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            received.countDown();
            try {
                if (release.await(10, TimeUnit.SECONDS)) {
                    exchange.sendResponseHeaders(204, -1);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(handlers);
        server.start();
        try {
            ApiClient client = ApiClient.forEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    1000, 150);
            Future<?> request = callers.submit(() -> client.get("/"));
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            release.countDown();
            server.stop(0);
            callers.shutdownNow();
            handlers.shutdownNow();
        }
    }
}
