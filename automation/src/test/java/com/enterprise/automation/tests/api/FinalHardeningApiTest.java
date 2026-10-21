package com.enterprise.automation.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.demoapp.http.Request;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/** Concurrent API operations retain client-facing conflicts and unrelated field updates. */
@Test(groups = {"api", "regression"})
public class FinalHardeningApiTest extends BaseApiTest {

    public void duplicateCreationReturnsOneCreatedAndOneConflict() throws Exception {
        CustomerData data = TestDataFactory.newCustomer();
        List<Response> results = race(() -> admin().customers().create(
                com.enterprise.automation.api.models.CustomerRequest.from(data)),
                () -> admin().customers().create(com.enterprise.automation.api.models.CustomerRequest.from(data)));
        for (Response response : results) {
            if (response.statusCode() == 201) {
                long id = response.jsonPath().getLong("id");
                CleanupRegistry.register("delete duplicate race customer", () -> admin().customers().deleteCustomerIfExists(id));
            }
        }
        assertThat(results).extracting(Response::statusCode).containsExactlyInAnyOrder(201, 409);
        assertThat(admin().customers().findCustomers(data.email()).stream()
                .filter(customer -> customer.email().equalsIgnoreCase(data.email())).count()).isEqualTo(1);
    }

    public void concurrentPatchesPreserveBothIndependentFields() throws Exception {
        CustomerResponse created = admin().customers().createCustomer(TestDataFactory.newCustomer());
        CleanupRegistry.register("delete patch race customer", () -> admin().customers().deleteCustomerIfExists(created.id()));
        List<Response> results = race(() -> admin().customers().update(created.id(), Map.of("city", "Patch City")),
                () -> admin().customers().update(created.id(), Map.of("phone", "+1-555-0199")));
        assertThat(results).extracting(Response::statusCode).containsOnly(200);
        CustomerResponse stored = admin().customers().getCustomer(created.id());
        assertThat(stored.city()).isEqualTo("Patch City");
        assertThat(stored.phone()).isEqualTo("+1-555-0199");
    }

    public void oversizedRequestIsRejectedWithoutCreatingARecord() {
        String body = JsonMapper.toJson(Map.of("firstName", "x".repeat(Request.MAX_BODY_BYTES),
                "lastName", "Oversized", "email", "oversized@example.test"));
        assertThat(admin().client().postRaw("/customers", body).statusCode()).isEqualTo(413);
        assertThat(admin().customers().findCustomers("oversized@example.test")).isEmpty();
    }

    private static List<Response> race(java.util.concurrent.Callable<Response> first,
            java.util.concurrent.Callable<Response> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Response> one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Race start timed out");
                }
                return first.call();
            });
            Future<Response> two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Race start timed out");
                }
                return second.call();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
