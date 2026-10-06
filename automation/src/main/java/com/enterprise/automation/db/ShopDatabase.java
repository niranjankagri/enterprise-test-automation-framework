package com.enterprise.automation.db;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The application's database as tests need it: named queries for customers, products, orders and
 * users. It plays the role page objects play for the UI: SQL lives here, once, and tests ask
 * business questions ({@code customerByEmail}, {@code stockOf}).
 */
public final class ShopDatabase {

    // Runs the (parameterized) SQL below
    private final QueryExecutor sql;

    public ShopDatabase(QueryExecutor sql) {
        this.sql = sql;
    }

    /** The database of the configured environment (skips the test where there is no access). */
    public static ShopDatabase fromConfig() {
        return new ShopDatabase(new QueryExecutor(DatabaseConnection.fromConfig()));
    }

    /** Direct access for one-off queries in a test (e.g. listing the schema's tables). */
    public QueryExecutor sql() {
        return sql;
    }

    /** The customer row with this email (case-insensitive, like the application's uniqueness rule). */
    public Optional<Map<String, Object>> customerByEmail(String email) {
        return sql.queryForOne("SELECT * FROM customers WHERE LOWER(email) = LOWER(?)", email);
    }

    /** The customer row with this id. */
    public Optional<Map<String, Object>> customerById(long id) {
        return sql.queryForOne("SELECT * FROM customers WHERE id = ?", id);
    }

    /** The product row with this SKU. */
    public Optional<Map<String, Object>> productBySku(String sku) {
        return sql.queryForOne("SELECT * FROM products WHERE sku = ?", sku);
    }

    /** Units in stock right now, as stored (not as the UI shows them). */
    public int stockOf(String sku) {
        return ((Number) sql.queryForValue("SELECT stock FROM products WHERE sku = ?", sku)).intValue();
    }

    /** The order header row (status, total, customer). */
    public Optional<Map<String, Object>> orderById(long id) {
        return sql.queryForOne("SELECT * FROM orders WHERE id = ?", id);
    }

    /** Lines of an order with the product SKU, in insertion order. */
    public List<Map<String, Object>> orderItems(long orderId) {
        return sql.queryForList("SELECT i.*, p.sku FROM order_items i JOIN products p ON p.id = i.product_id"
                + " WHERE i.order_id = ? ORDER BY i.id", orderId);
    }

    /** How many orders a customer has (0 proves a rejected order left nothing behind). */
    public long orderCountOf(long customerId) {
        return sql.count("SELECT COUNT(*) FROM orders WHERE customer_id = ?", customerId);
    }

    /** The account row, including password hash and salt (masked in the report). */
    public Optional<Map<String, Object>> userByUsername(String username) {
        return sql.queryForOne("SELECT * FROM app_users WHERE username = ?", username);
    }

    /**
     * Hard-deletes a product that no order references. The API only deactivates products (they
     * are kept for order history), so this is the clean-up for products created by tests.
     */
    public int deleteUnusedProduct(long id) {
        // NOT EXISTS guard: a product still referenced by an order line is never removed
        return sql.update("DELETE FROM products WHERE id = ? AND NOT EXISTS"
                + " (SELECT 1 FROM order_items WHERE product_id = ?)", id, id);
    }
}
