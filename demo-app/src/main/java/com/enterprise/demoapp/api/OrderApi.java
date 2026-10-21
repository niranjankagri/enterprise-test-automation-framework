package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.http.ApiException;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /api/orders}: list, get, place, change status, delete.
 *
 * <p>Status flow: PLACED → SHIPPED → DELIVERED, or PLACED → CANCELLED. Placing an order reserves
 * stock; cancelling or deleting a PLACED order gives it back.
 */
public final class OrderApi {

    private static final String QUANTITY_ERROR = "Quantity must be a whole number between 1 and " + Integer.MAX_VALUE;
    private static final int MAX_ITEMS = 1000;
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    // For each status, the statuses an order may move to next
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLACED", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("DELIVERED"),
            "DELIVERED", Set.of(),
            "CANCELLED", Set.of());

    private final Database database;

    /** Handlers read and write through this database. */
    public OrderApi(Database database) {
        this.database = database;
    }

    /** {@code GET /api/orders?customerId=&status=}, newest first. */
    public Response list(Request request) {
        String customerId = request.query("customerId");
        String status = request.query("status");
        // Build the filter: "1 = 1" lets every condition start with AND
        StringBuilder sql = new StringBuilder("SELECT id FROM orders WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (customerId != null) {
            sql.append(" AND customer_id = ?");
            try {
                args.add(Long.parseLong(customerId));
            } catch (NumberFormatException e) {
                throw new ApiException(400, "customerId must be a number");
            }
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY id DESC");
        return Sql.run(database, c -> {
            // First the matching ids...
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) {
                    ps.setObject(i + 1, args.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
            }
            // ...then each full order with its lines (same shape as GET /orders/{id})
            List<Map<String, Object>> orders = new ArrayList<>();
            for (long id : ids) {
                orders.add(find(c, id));
            }
            return Response.ok(orders);
        });
    }

    /** {@code GET /api/orders/{id}}; 404 if unknown. */
    public Response get(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> Response.ok(find(c, id)));
    }

    /** {@code POST {customerId, items: [{productId, quantity}]}}. */
    public Response create(Request request) {
        JsonNode body = request.body();
        // Shape checks first (400), business checks later (404/409)
        Validator v = new Validator(body, false);
        JsonNode customerNode = body.get("customerId");
        if (customerNode == null || !customerNode.canConvertToLong() || !customerNode.isIntegralNumber()
                || customerNode.longValue() < 1) {
            v.error("customerId", "Customer is required");
        }
        JsonNode items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            v.error("items", "At least one item is required");
        } else if (items.size() > MAX_ITEMS) {
            v.error("items", "At most 1000 items are allowed");
        } else {
            // Each line needs a product id and a quantity of at least 1; errors name the line index
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                if (item.get("productId") == null || !item.get("productId").isIntegralNumber()
                        || !item.get("productId").canConvertToLong() || item.get("productId").longValue() < 1) {
                    v.error("items[" + i + "].productId", "Product is required");
                }
                JsonNode quantity = item.get("quantity");
                if (quantity == null || !quantity.isIntegralNumber() || !quantity.canConvertToInt()
                        || quantity.intValue() < 1) {
                    v.error("items[" + i + "].quantity", QUANTITY_ERROR);
                }
            }
        }
        v.validate();

        long customerId = customerNode.longValue();
        // One transaction: either the whole order is stored and all stock reserved, or nothing changes
        return Sql.transaction(database, c -> {
            // 404 for an unknown customer; 409 for an inactive one
            Map<String, Object> customer = CustomerApi.find(c, customerId);
            if (!"ACTIVE".equals(customer.get("status"))) {
                throw new ApiException(409, "Customer " + customerId + " is inactive");
            }
            BigDecimal total = BigDecimal.ZERO;
            // [productId, quantity] per line, and the unit price at the time of ordering
            List<long[]> lines = new ArrayList<>();
            List<BigDecimal> prices = new ArrayList<>();
            for (JsonNode item : items) {
                long productId = item.get("productId").longValue();
                int quantity = item.get("quantity").intValue();
                // 404 for an unknown product
                Map<String, Object> product = ProductApi.find(c, productId);
                // Deactivated products cannot be sold anymore
                if (!(Boolean) product.get("active")) {
                    throw new ApiException(409, "Product " + product.get("sku") + " is no longer sold");
                }
                // Not enough stock: 409 and the transaction rolls back any stock already reserved
                if ((Integer) product.get("stock") < quantity) {
                    throw new ApiException(409, "Insufficient stock for " + product.get("sku") + ": "
                            + product.get("stock") + " left, " + quantity + " requested");
                }
                BigDecimal price = (BigDecimal) product.get("price");
                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
                lines.add(new long[] {productId, quantity});
                prices.add(price);
            }
            if (total.compareTo(MAX_TOTAL) > 0) {
                throw ApiException.validation(Map.of("items", "Order total must be at most 9999999999.99"));
            }
            // Lock product rows in stable ID order so overlapping multi-product orders cannot deadlock
            // just because callers supplied the same lines in a different order.
            List<long[]> reservationOrder = new ArrayList<>(lines);
            reservationOrder.sort(Comparator.comparingLong(line -> line[0]));
            for (long[] line : reservationOrder) {
                // Reserve atomically; a competing order may have used the stock read above.
                reserveStock(c, line[0], (int) line[1]);
            }
            // The order header with the computed total
            long orderId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (customer_id, status, total) VALUES (?, 'PLACED', ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, customerId);
                ps.setBigDecimal(2, total);
                ps.executeUpdate();
                orderId = Sql.generatedId(ps);
            }
            // One row per line, keeping the price that applied when ordering
            for (int i = 0; i < lines.size(); i++) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, orderId);
                    ps.setLong(2, lines.get(i)[0]);
                    ps.setInt(3, (int) lines.get(i)[1]);
                    ps.setBigDecimal(4, prices.get(i));
                    ps.executeUpdate();
                }
            }
            return Response.created(find(c, orderId), "/api/orders/" + orderId);
        });
    }

    /** {@code PATCH {status}}: only allowed transitions; cancelling returns the stock. */
    public Response updateStatus(Request request) {
        long id = request.pathId("id");
        Validator v = new Validator(request.body(), false);
        // Must be one of the known statuses
        String status = v.oneOf("status", "Status", TRANSITIONS.keySet(), true);
        v.validate();
        return Sql.transaction(database, c -> {
            String current = lockStatus(c, id);
            // e.g. DELIVERED -> CANCELLED is not allowed
            if (!TRANSITIONS.get(current).contains(status)) {
                throw new ApiException(409, "Order " + id + " cannot go from " + current + " to " + status);
            }
            // The locked status is part of the update guard: a competing transition cannot repeat the same release.
            int affectedRows;
            try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET status = ? WHERE id = ? AND status = ?")) {
                ps.setString(1, status);
                ps.setLong(2, id);
                ps.setString(3, current);
                affectedRows = ps.executeUpdate();
            }
            if (affectedRows != 1) {
                throw new SQLException("Expected to update one order row but updated " + affectedRows);
            }
            // The status change and stock return commit or roll back together.
            if ("CANCELLED".equals(status)) {
                restoreStock(c, id);
            }
            return Response.ok(find(c, id));
        });
    }

    /** {@code DELETE}: removes the order (test clean-up); a PLACED order gives its stock back. */
    public Response delete(Request request) {
        long id = request.pathId("id");
        return Sql.transaction(database, c -> {
            String status = lockStatus(c, id);
            // Only PLACED orders still hold reserved stock (shipped/delivered stock is gone, cancelled already returned)
            if ("PLACED".equals(status)) {
                restoreStock(c, id);
            }
            // Order lines are deleted with the order (ON DELETE CASCADE)
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM orders WHERE id = ?")) {
                ps.setLong(1, id);
                int affectedRows = ps.executeUpdate();
                if (affectedRows != 1) {
                    throw new SQLException("Expected to delete one order row but deleted " + affectedRows);
                }
            }
            return Response.noContent();
        });
    }

    /** Locks one order through the transaction so cancellation and deletion cannot restore the same stock twice. */
    private static String lockStatus(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM orders WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Order", orderId);
                }
                return rs.getString("status");
            }
        }
    }

    /** Gives every line's quantity back to its product. */
    private static void restoreStock(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id, quantity FROM order_items WHERE order_id = ? ORDER BY product_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    adjustStock(c, rs.getLong(1), rs.getInt(2));
                }
            }
        }
    }

    /** Atomically reserves available stock or reports the current business conflict. */
    private static void reserveStock(Connection c, long productId, int quantity) throws SQLException {
        int affectedRows;
        try (PreparedStatement ps = c.prepareStatement("UPDATE products SET stock = stock - ?"
                + " WHERE id = ? AND active = TRUE AND stock >= ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            affectedRows = ps.executeUpdate();
        }
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows != 0) {
            throw new SQLException("Expected to reserve one product row but updated " + affectedRows);
        }

        // The row may have changed while this transaction waited for another reservation.
        Map<String, Object> current = ProductApi.find(c, productId);
        if (!(Boolean) current.get("active")) {
            throw new ApiException(409, "Product " + current.get("sku") + " is no longer sold");
        }
        int available = (Integer) current.get("stock");
        throw new ApiException(409, "Insufficient stock for " + current.get("sku") + ": "
                + available + " left, " + quantity + " requested");
    }

    /** Adds {@code delta} units to a product's stock and requires exactly one matching product. */
    private static void adjustStock(Connection c, long productId, int delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE products SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setLong(2, productId);
            int affectedRows = ps.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Expected to update one product row but updated " + affectedRows);
            }
        }
    }

    /** One order with customer name and lines; 404 if it does not exist. */
    static Map<String, Object> find(Connection c, long id) throws SQLException {
        Map<String, Object> order = new LinkedHashMap<>();
        // Header joined with the customer for the display name
        try (PreparedStatement ps = c.prepareStatement("SELECT o.id, o.customer_id, c.first_name, c.last_name,"
                + " o.status, o.total, o.created_at FROM orders o JOIN customers c ON c.id = o.customer_id"
                + " WHERE o.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Order", id);
                }
                order.put("id", rs.getLong("id"));
                order.put("customerId", rs.getLong("customer_id"));
                order.put("customerName", rs.getString("first_name") + " " + rs.getString("last_name"));
                order.put("status", rs.getString("status"));
                order.put("total", rs.getBigDecimal("total"));
                order.put("createdAt", rs.getTimestamp("created_at").toInstant());
            }
        }
        // Lines joined with the product for SKU and name, in the order they were added
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT i.product_id, p.sku, p.name, i.quantity, i.unit_price"
                + " FROM order_items i JOIN products p ON p.id = i.product_id WHERE i.order_id = ? ORDER BY i.id")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", rs.getLong("product_id"));
                    item.put("sku", rs.getString("sku"));
                    item.put("name", rs.getString("name"));
                    item.put("quantity", rs.getInt("quantity"));
                    item.put("unitPrice", rs.getBigDecimal("unit_price"));
                    // Line total = unit price x quantity
                    item.put("lineTotal", rs.getBigDecimal("unit_price").multiply(BigDecimal.valueOf(rs.getInt("quantity"))));
                    items.add(item);
                }
            }
        }
        order.put("items", items);
        return order;
    }
}
