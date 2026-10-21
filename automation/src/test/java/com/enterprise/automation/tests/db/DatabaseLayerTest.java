package com.enterprise.automation.tests.db;

import static com.enterprise.automation.db.DatabaseAssertions.assertNoRow;
import static com.enterprise.automation.db.DatabaseAssertions.assertRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.TestDataProviders;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** The database layer itself: connection, parameterized queries, assertions. */
@Test(groups = {"db", "regression"})
public class DatabaseLayerTest {

    private ShopDatabase db;

    /** In a configuration method, so "no database access" skips the class instead of failing it. */
    @BeforeClass(alwaysRun = true)
    public void connect() {
        db = ShopDatabase.fromConfig();
    }

    /** The database layer connects and sees exactly the application's five tables. */
    @Test(groups = "smoke")
    public void connectsAndReadsTheSchema() {
        // The standard information_schema lists the application's tables (H2 keeps them in PUBLIC)
        List<Map<String, Object>> tables = db.sql().queryForList(
                "SELECT LOWER(table_name) AS name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"
                        + " ORDER BY name");

        assertThat(tables).extracting(t -> t.get("name"))
                .containsExactly("app_users", "customers", "order_items", "orders", "products");
    }

    /** Each seeded product's row matches the reference data, column by column. */
    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class)
    public void catalogueRowsMatchTheReferenceData(String sku, ProductData expected) {
        // The stored row of each reference product, column by column
        assertRow(db.productBySku(sku), "product " + sku)
                .hasValue("name", expected.name())
                .hasValue("category", expected.category())
                .hasValue("price", expected.price())
                .hasValue("active", true);
    }

    /**
     * SQL injection text is bound as a parameter and matches nothing, proving queries are not built by concatenation.
     */
    public void parametersAreBoundNotConcatenated() {
        // Classic SQL injection text: concatenated into SQL it would match every row
        String hostile = "x' OR '1'='1";

        // Bound as a parameter it is just an email that does not exist
        assertNoRow(db.customerByEmail(hostile), "customer with a quote in the email");
        assertThat(db.sql().count("SELECT COUNT(*) FROM customers WHERE email = ?", hostile)).isZero();
    }

    /** A single-row query that finds several rows fails instead of picking one. */
    public void queryForOneRejectsSeveralRows() {
        // All products for a "single row" query: a mistake that must not pass silently
        assertThatThrownBy(() -> db.sql().queryForOne("SELECT * FROM products"))
                .hasMessageContaining("at most one row");
    }

    /** A failed row check names the column and shows the real row. */
    public void assertionFailuresShowTheRow() {
        // A deliberately wrong expectation: the message must name the column and show the real row
        assertThatThrownBy(() -> assertRow(db.productBySku("ACC-3002"), "product ACC-3002").hasValue("name", "Wrong"))
                .hasMessageContaining("product ACC-3002.name")
                .hasMessageContaining("Wireless Mouse");
    }

    /** The seeded catalogue in the database has as many products as the reference file. */
    public void catalogueSizeMatches() {
        // Seeded products only (TST- products come and go with other tests)
        long seeded = db.sql().count("SELECT COUNT(*) FROM products WHERE sku NOT LIKE 'TST-%'");

        assertThat(seeded).isEqualTo(TestDataFactory.catalogue().size());
    }

    /** Database uniqueness constraints reject duplicates without inserting another row. */
    public void uniqueConstraintsRejectDuplicateKeys() {
        assertConstraintViolation("INSERT INTO app_users (username, password_hash, salt, full_name, role)"
                + " VALUES (?, ?, ?, ?, ?)", "admin", "synthetic-hash", "synthetic-salt", "Duplicate", "VIEWER");
        assertThat(db.sql().count("SELECT COUNT(*) FROM app_users WHERE username = ?", "admin")).isEqualTo(1);

        assertConstraintViolation("INSERT INTO customers (first_name, last_name, email) VALUES (?, ?, ?)",
                "Duplicate", "Customer", "ava.patel@example.com");
        assertThat(db.sql().count("SELECT COUNT(*) FROM customers WHERE email = ?", "ava.patel@example.com"))
                .isEqualTo(1);

        assertConstraintViolation("INSERT INTO products (sku, name, category, price, stock) VALUES (?, ?, ?, ?, ?)",
                "ACC-3002", "Duplicate", "Accessories", new BigDecimal("49.00"), 1);
        assertThat(db.sql().count("SELECT COUNT(*) FROM products WHERE sku = ?", "ACC-3002")).isEqualTo(1);
    }

    /** Foreign keys reject missing references, protect referenced rows, and cascade order-line deletion. */
    public void foreignKeysProtectReferencesAndCascadeOrderLines() {
        long customerId = -ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
        long productId = customerId - 1;
        long orderId = customerId - 2;
        long itemId = customerId - 3;
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "h02-" + suffix + "@example.test";
        String sku = "H02-" + suffix.substring(0, 20);

        try {
            db.sql().update("INSERT INTO customers (id, first_name, last_name, email) VALUES (?, ?, ?, ?)",
                    customerId, "H02", "Fixture", email);
            db.sql().update("INSERT INTO products (id, sku, name, category, price, stock) VALUES (?, ?, ?, ?, ?, ?)",
                    productId, sku, "H02 fixture", "Test", new BigDecimal("1.00"), 1);

            assertConstraintViolation("INSERT INTO orders (customer_id, status, total) VALUES (?, ?, ?)",
                    customerId - 10, "PLACED", new BigDecimal("1.00"));
            assertThat(db.sql().count("SELECT COUNT(*) FROM orders WHERE customer_id = ?", customerId - 10))
                    .as("invalid customer reference inserts no order").isZero();
            assertConstraintViolation("INSERT INTO order_items (order_id, product_id, quantity, unit_price)"
                    + " VALUES (?, ?, ?, ?)", orderId - 10, productId, 1, new BigDecimal("1.00"));
            assertThat(db.sql().count("SELECT COUNT(*) FROM order_items WHERE order_id = ?", orderId - 10))
                    .as("invalid order reference inserts no line").isZero();

            db.sql().update("INSERT INTO orders (id, customer_id, status, total) VALUES (?, ?, ?, ?)", orderId,
                    customerId, "PLACED", new BigDecimal("1.00"));
            assertConstraintViolation("INSERT INTO order_items (order_id, product_id, quantity, unit_price)"
                    + " VALUES (?, ?, ?, ?)", orderId, productId - 10, 1, new BigDecimal("1.00"));
            assertThat(db.sql().count("SELECT COUNT(*) FROM order_items WHERE order_id = ?", orderId)).isZero();

            db.sql().update("INSERT INTO order_items (id, order_id, product_id, quantity, unit_price)"
                    + " VALUES (?, ?, ?, ?, ?)", itemId, orderId, productId, 1, new BigDecimal("1.00"));
            assertConstraintViolation("DELETE FROM customers WHERE id = ?", customerId);
            assertConstraintViolation("DELETE FROM products WHERE id = ?", productId);
            assertThat(db.sql().count("SELECT COUNT(*) FROM customers WHERE id = ?", customerId)).isEqualTo(1);
            assertThat(db.sql().count("SELECT COUNT(*) FROM products WHERE id = ?", productId)).isEqualTo(1);
            assertThat(db.sql().count("SELECT COUNT(*) FROM order_items WHERE order_id = ?", orderId)).isEqualTo(1);

            assertThat(db.sql().update("DELETE FROM orders WHERE id = ?", orderId)).isEqualTo(1);
            assertThat(db.sql().count("SELECT COUNT(*) FROM order_items WHERE order_id = ?", orderId))
                    .as("order deletion cascades to its lines").isZero();
            assertThat(db.sql().update("DELETE FROM customers WHERE id = ?", customerId)).isEqualTo(1);
            assertThat(db.sql().update("DELETE FROM products WHERE id = ?", productId)).isEqualTo(1);
            assertThat(db.sql().count("SELECT COUNT(*) FROM customers WHERE id = ?", customerId)).isZero();
            assertThat(db.sql().count("SELECT COUNT(*) FROM products WHERE id = ?", productId)).isZero();
        } finally {
            db.sql().update("DELETE FROM orders WHERE id = ?", orderId);
            db.sql().update("DELETE FROM products WHERE id = ?", productId);
            db.sql().update("DELETE FROM customers WHERE id = ?", customerId);
        }
    }

    private void assertConstraintViolation(String sql, Object... parameters) {
        assertThatThrownBy(() -> db.sql().update(sql, parameters))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }
}
