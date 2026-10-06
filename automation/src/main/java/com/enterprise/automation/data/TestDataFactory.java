package com.enterprise.automation.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The one place tests get their data from.
 *
 * <p>Generated data ({@link #newCustomer()}, {@link #newProduct()}, {@link #newUser(String)}) is
 * unique and valid by default; a test that needs something specific changes only that field
 * ({@code newCustomer().withCity(null)}), so the intent of the test stays visible.
 *
 * <p>Reference data ({@link #catalogue()}) is the seeded product catalogue, read from
 * {@code testdata/products.json}, so expected values are maintained in one data file instead of
 * being repeated in test code.
 */
public final class TestDataFactory {

    private static final String CATALOGUE = "testdata/products.json";
    // The categories the application's UI offers in its filter
    private static final List<String> CATEGORIES = List.of("Laptops", "Monitors", "Accessories", "Audio");

    // Static factory only
    private TestDataFactory() {
    }

    /** A new, valid customer with a unique email. */
    public static CustomerData newCustomer() {
        // The email is built from the same names, so the data looks consistent in reports
        String firstName = RandomDataGenerator.firstName();
        String lastName = RandomDataGenerator.lastName();
        return new CustomerData(firstName, lastName, RandomDataGenerator.email(firstName, lastName),
                RandomDataGenerator.phone(), RandomDataGenerator.city());
    }

    /** A new, valid product with a unique SKU, price between 5 and 500 and some stock. */
    public static ProductData newProduct() {
        // Two decimals, like real prices (and like the DECIMAL(10,2) column)
        BigDecimal price = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(5, 500))
                .setScale(2, RoundingMode.HALF_UP);
        String category = CATEGORIES.get(ThreadLocalRandom.current().nextInt(CATEGORIES.size()));
        return new ProductData(RandomDataGenerator.sku(), RandomDataGenerator.productName(), category, price,
                ThreadLocalRandom.current().nextInt(10, 100));
    }

    /** A new back-office account with a unique username and a valid password. */
    public static UserData newUser(String role) {
        String first = RandomDataGenerator.firstName();
        String last = RandomDataGenerator.lastName();
        // A generated password that satisfies the API's minimum length and is unique per user
        return new UserData(RandomDataGenerator.username(), "Pw-" + RandomDataGenerator.uniqueSuffix() + "!",
                first + " " + last, role);
    }

    /** The seeded product catalogue (reference data, never modified by tests). */
    public static List<ProductData> catalogue() {
        return JsonDataReader.readList(CATALOGUE, ProductData.class);
    }

    /** A product of the seeded catalogue by SKU. */
    public static ProductData catalogueProduct(String sku) {
        return catalogue().stream().filter(p -> p.sku().equals(sku)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No product " + sku + " in " + CATALOGUE));
    }

    /** An order of the given lines for {@code customer}. */
    public static OrderData order(CustomerData customer, OrderData.Line... lines) {
        return new OrderData(customer, List.of(lines));
    }

    /** One order line: a catalogue product (by SKU) and a quantity. */
    public static OrderData.Line line(String sku, int quantity) {
        return new OrderData.Line(catalogueProduct(sku), quantity);
    }
}
