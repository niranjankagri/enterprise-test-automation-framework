package com.enterprise.automation.tests.base;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.enterprise.automation.data.CsvDataReader;
import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.testng.annotations.DataProvider;

/**
 * TestNG DataProviders backed by the files in {@code src/test/resources/testdata}. Adding a row
 * to a file adds a test case; no code changes.
 *
 * <p>Each row's first value is a readable case name, so reports show "email without @" instead of
 * a list of parameters.
 */
public final class TestDataProviders {

    private TestDataProviders() {
    }

    /** {@code login-negative.csv}: case, username, password, expectedField, expectedMessage. */
    @DataProvider(name = "loginNegative")
    public static Object[][] loginNegative() {
        return rows("testdata/login-negative.csv", "case", "username", "password", "expectedField", "expectedMessage");
    }

    /** {@code invalid-customers.csv}: case, field, label, value, error. */
    @DataProvider(name = "invalidCustomers")
    public static Object[][] invalidCustomers() {
        return rows("testdata/invalid-customers.csv", "case", "field", "label", "value", "error");
    }

    /** {@code products.json}: SKU and product, one row per catalogue product. */
    @DataProvider(name = "catalogue")
    public static Object[][] catalogue() {
        return TestDataFactory.catalogue().stream().map(p -> new Object[] {p.sku(), p}).toArray(Object[][]::new);
    }

    /** Each catalogue category with the product names the JSON assigns to it. */
    @DataProvider(name = "categories")
    public static Object[][] categories() {
        // TreeMap: categories in alphabetical order, so the cases always run in the same order
        Map<String, List<String>> byCategory = TestDataFactory.catalogue().stream()
                .collect(groupingBy(ProductData::category, TreeMap::new, mapping(ProductData::name, toList())));
        return byCategory.entrySet().stream().map(e -> new Object[] {e.getKey(), e.getValue()}).toArray(Object[][]::new);
    }

    /** CSV rows as TestNG parameter arrays, with the columns in the given order (= method parameter order). */
    private static Object[][] rows(String resource, String... columns) {
        return CsvDataReader.read(resource).stream()
                .map(row -> Arrays.stream(columns).map(row::get).toArray())
                .toArray(Object[][]::new);
    }
}
