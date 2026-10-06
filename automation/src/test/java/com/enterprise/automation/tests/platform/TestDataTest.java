package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.CsvDataReader;
import com.enterprise.automation.data.CustomerData;
import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.data.UserData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.testng.annotations.Test;

/** Unit tests of the test-data layer: no browser, no application. */
@Test(groups = "unit")
public class TestDataTest {

    /** Same pattern the application uses to validate emails. */
    private static final String EMAIL = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$";

    public void generatedCustomersAreValidAndUniqueEvenInParallel() {
        Set<String> emails = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 500).parallel().forEach(i -> {
            CustomerData customer = TestDataFactory.newCustomer();
            assertThat(customer.email()).matches(EMAIL);
            assertThat(customer.phone()).matches("^\\+1-555-\\d{4}$");
            assertThat(customer.firstName()).isNotBlank().matches("[A-Za-z]+");
            emails.add(customer.email());
        });
        assertThat(emails).hasSize(500);
    }

    public void generatedProductsAndUsersAreValid() {
        ProductData product = TestDataFactory.newProduct();
        assertThat(product.sku()).startsWith("TST-").hasSizeLessThanOrEqualTo(30);
        assertThat(product.price()).isBetween(new BigDecimal("5.00"), new BigDecimal("500.00"));
        assertThat(product.stock()).isPositive();

        UserData user = TestDataFactory.newUser("VIEWER");
        assertThat(user.password()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(user.toString()).doesNotContain(user.password());
    }

    public void catalogueIsReadFromJson() {
        List<ProductData> catalogue = TestDataFactory.catalogue();

        assertThat(catalogue).hasSize(8);
        ProductData mouse = TestDataFactory.catalogueProduct("ACC-3002");
        assertThat(mouse.name()).isEqualTo("Wireless Mouse");
        assertThat(mouse.displayPrice()).isEqualTo("$49.00");
        assertThat(TestDataFactory.catalogueProduct("LAP-1001").displayPrice()).isEqualTo("$1,199.00");
    }

    public void orderTotalsAreComputedFromTheCatalogue() {
        OrderData order = TestDataFactory.order(TestDataFactory.newCustomer(),
                TestDataFactory.line("LAP-1001", 2), TestDataFactory.line("ACC-3001", 1));

        assertThat(order.total()).isEqualByComparingTo("2477.00");
        assertThat(order.displayTotal()).isEqualTo("$2,477.00");
        assertThat(order.itemCount()).isEqualTo(3);
    }

    public void csvIsReadWithHeadersCommentsAndQuotes() {
        List<Map<String, String>> rows = CsvDataReader.read("testdata/invalid-customers.csv");

        assertThat(rows).isNotEmpty().allSatisfy(r -> assertThat(r).containsKeys("case", "field", "label", "value", "error"));
        assertThat(rows).filteredOn(r -> r.get("case").equals("phone with letters")).singleElement()
                .satisfies(r -> assertThat(r.get("error")).isEqualTo("Phone must contain 7-20 digits, spaces, +, - or ()"));
        assertThat(rows).filteredOn(r -> r.get("case").equals("email missing")).singleElement()
                .satisfies(r -> assertThat(r.get("value")).isEmpty());
    }

    public void missingDataFilesFailClearly() {
        assertThatThrownBy(() -> CsvDataReader.read("testdata/nope.csv")).hasMessageContaining("testdata/nope.csv");
        assertThatThrownBy(() -> TestDataFactory.catalogueProduct("XXX-0000")).hasMessageContaining("XXX-0000");
    }

    public void cleanupRunsNewestFirstAndSurvivesFailures() {
        List<String> ran = new ArrayList<>();
        CleanupRegistry.register("customer", () -> ran.add("customer"));
        CleanupRegistry.register("broken", () -> {
            throw new IllegalStateException("already gone");
        });
        CleanupRegistry.register("failed check", () -> {
            throw new AssertionError("Expected HTTP 204 but got 404");
        });
        CleanupRegistry.register("order", () -> ran.add("order"));

        int failures = CleanupRegistry.runAll();

        assertThat(ran).containsExactly("order", "customer");
        assertThat(failures).isEqualTo(2);
        assertThat(CleanupRegistry.pending()).isZero();
    }
}
