package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.CustomerRequest;
import com.enterprise.automation.api.models.CustomerResponse;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.OrderRequest;
import com.enterprise.automation.api.services.CustomerService;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.Test;

/** {@code /api/customers}: every method, status codes, headers, payloads and schemas. */
@Test(groups = {"api", "regression"})
public class CustomerApiTest extends BaseApiTest {

    private CustomerService customers() {
        return admin().customers();
    }

    /** Creates a customer through the API and registers its deletion. */
    private CustomerResponse givenCustomer(CustomerData data) {
        CustomerResponse created = customers().createCustomer(data);
        CleanupRegistry.register("delete customer " + created.id(), () -> customers().deleteCustomerIfExists(created.id()));
        return created;
    }

    @Test(groups = {"smoke", "sanity"})
    public void postCreatesACustomer() {
        CustomerData data = TestDataFactory.newCustomer();

        // The raw call: status, headers and body are what is tested here
        Response response = customers().create(CustomerRequest.from(data));

        expectStatus(response, 201);
        matchesSchema(response, "customer");
        CustomerResponse created = JsonMapper.fromJson(response.asString(), CustomerResponse.class);
        // Registered as soon as the id is known, before the assertions that could fail
        CleanupRegistry.register("delete customer " + created.id(), () -> customers().deleteCustomerIfExists(created.id()));
        // 201 must say where the new resource lives; every response carries a request id
        assertThat(response.getHeader("Location")).isEqualTo("/api/customers/" + created.id());
        assertThat(response.getHeader("X-Request-Id")).as("traceable request").isNotBlank();
        // Everything sent comes back, and new customers are ACTIVE by default
        assertThat(created).returns(data.firstName(), CustomerResponse::firstName)
                .returns(data.lastName(), CustomerResponse::lastName)
                .returns(data.email(), CustomerResponse::email)
                .returns(data.phone(), CustomerResponse::phone)
                .returns(data.city(), CustomerResponse::city)
                .returns("ACTIVE", CustomerResponse::status);
    }

    public void getReturnsTheCustomer() {
        CustomerResponse created = givenCustomer(TestDataFactory.newCustomer());

        Response response = expectStatus(customers().get(created.id()), 200);

        // GET returns exactly what POST returned (records compare field by field)
        matchesSchema(response, "customer");
        assertThat(JsonMapper.fromJson(response.asString(), CustomerResponse.class)).isEqualTo(created);
    }

    public void searchFindsByEmailNameAndCity() {
        // A city nobody else has, so a city search can only find this customer
        CustomerData data = TestDataFactory.newCustomer().withCity("Reykjavik-" + System.nanoTime());
        CustomerResponse created = givenCustomer(data);

        // The list response follows its schema
        Response response = expectStatus(customers().list(data.email()), 200);
        matchesSchema(response, "customer-list");

        // Found by email, by upper-cased city (case-insensitive), and nothing for an unknown term
        assertThat(customers().findCustomers(data.email())).extracting(CustomerResponse::id).containsExactly(created.id());
        assertThat(customers().findCustomers(data.city().toUpperCase())).as("case-insensitive")
                .extracting(CustomerResponse::id).containsExactly(created.id());
        assertThat(customers().findCustomers("no-such-customer-xyz")).isEmpty();
    }

    public void putReplacesTheWholeCustomer() {
        CustomerResponse created = givenCustomer(TestDataFactory.newCustomer());
        // Completely different data, without a phone
        CustomerData replacement = TestDataFactory.newCustomer().withPhone(null);

        Response response = customers().replace(created.id(), CustomerRequest.from(replacement).withStatus("INACTIVE"));

        // Same id and creation time, every other field replaced (omitted phone becomes null)
        expectStatus(response, 200);
        CustomerResponse updated = JsonMapper.fromJson(response.asString(), CustomerResponse.class);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.email()).isEqualTo(replacement.email());
        assertThat(updated.phone()).as("PUT without phone clears it").isNull();
        assertThat(updated.status()).isEqualTo("INACTIVE");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    public void patchChangesOnlyTheGivenFields() {
        CustomerResponse created = givenCustomer(TestDataFactory.newCustomer());

        CustomerResponse patched = JsonMapper.fromJson(
                expectStatus(customers().update(created.id(), Map.of("city", "Oslo")), 200).asString(),
                CustomerResponse.class);

        // City changed; every other field exactly as before
        assertThat(patched.city()).isEqualTo("Oslo");
        assertThat(patched).usingRecursiveComparison().ignoringFields("city").isEqualTo(created);
    }

    public void deleteRemovesTheCustomer() {
        // No clean-up registered: deleting it is the test
        CustomerResponse created = customers().createCustomer(TestDataFactory.newCustomer());

        Response response = customers().delete(created.id());

        // 204 with an empty body, and the customer is gone afterwards
        expectStatus(response, 204);
        assertThat(response.asString()).isEmpty();
        assertThat(error(expectStatus(customers().get(created.id()), 404)).message())
                .isEqualTo("Customer " + created.id() + " not found");
    }

    @Test(dataProvider = "invalidCustomers", dataProviderClass = TestDataProviders.class)
    public void invalidFieldsGive400WithTheFieldMessage(String testCase, String field, String label, String value,
                                                        String expected) {
        // A valid body as a mutable map, then the CSV row overwrites one field with an invalid value
        Map<String, Object> body = new HashMap<>(Map.of(
                "firstName", "Valid", "lastName", "Customer", "email", TestDataFactory.newCustomer().email()));
        body.put(field, value);

        // Same expected message as the UI test shows under the form field
        ErrorResponse error = error(expectStatus(customers().create(body), 400));

        assertThat(error.message()).isEqualTo("Validation failed");
        assertThat(error.fieldErrors()).as(testCase).containsEntry(field, expected);
    }

    public void allInvalidFieldsAreReportedTogether() {
        // Two names missing, a bad email and an unknown status in one request
        ErrorResponse error = error(expectStatus(customers().create(Map.of("email", "bad", "status", "UNKNOWN")), 400));

        // All four reported at once, nothing else
        assertThat(error.fieldErrors()).containsOnlyKeys("firstName", "lastName", "email", "status");
    }

    public void duplicateEmailGives409() {
        CustomerResponse existing = givenCustomer(TestDataFactory.newCustomer());
        // Upper-cased: uniqueness must ignore case
        CustomerData duplicate = TestDataFactory.newCustomer().withEmail(existing.email().toUpperCase());

        ErrorResponse error = error(expectStatus(customers().create(CustomerRequest.from(duplicate)), 409));

        assertThat(error.fieldErrors()).containsEntry("email", "Email is already in use");
    }

    public void customerWithOrdersCannotBeDeleted() {
        CustomerResponse customer = givenCustomer(TestDataFactory.newCustomer());
        // Give the customer one order (removed again after the test, before the customer)
        long productId = admin().products().getBySku("ACC-3001").id();
        long orderId = admin().orders().placeOrder(OrderRequest.of(customer.id(), OrderRequest.item(productId, 1))).id();
        CleanupRegistry.register("delete order " + orderId, () -> admin().orders().deleteOrder(orderId));

        ErrorResponse error = error(expectStatus(customers().delete(customer.id()), 409));

        assertThat(error.message()).contains("has orders");
    }

    public void unknownIdsGive404() {
        // GET, PATCH and DELETE on an id that cannot exist
        assertThat(error(expectStatus(customers().get(999_999), 404)).error()).isEqualTo("Not Found");
        expectStatus(customers().update(999_999, Map.of("city", "X")), 404);
        expectStatus(customers().delete(999_999), 404);
    }

    public void malformedRequestsGive400Or405() {
        // The raw client sends what the typed services cannot: a non-numeric id, broken JSON,
        // a JSON array instead of an object, and a method the path does not support
        assertThat(error(expectStatus(admin().client().get("/customers/abc"), 400)).message())
                .isEqualTo("Path parameter 'id' must be a number: abc");
        assertThat(error(expectStatus(admin().client().postRaw("/customers", "{not json"), 400)).message())
                .isEqualTo("Malformed JSON request body");
        assertThat(error(expectStatus(admin().client().postRaw("/customers", "[1, 2]"), 400)).message())
                .isEqualTo("Request body must be a JSON object");
        assertThat(error(expectStatus(admin().client().put("/customers", Map.of()), 405)).error())
                .isEqualTo("Method Not Allowed");
    }
}
