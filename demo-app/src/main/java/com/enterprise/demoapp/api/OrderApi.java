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

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLACED", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("DELIVERED"),
            "DELIVERED", Set.of(),
            "CANCELLED", Set.of());

    private final Database database;

    public OrderApi(Database database) {
        this.database = database;
    }

    /** {@code GET /api/orders?customerId=&status=}, newest first. */
    public Response list(Request request) {
        String customerId = request.query("customerId");
        String status = request.query("status");
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
            List<Map<String, Object>> orders = new ArrayList<>();
            for (long id : ids) {
                orders.add(find(c, id));
            }
            return Response.ok(orders);
        });
    }

    public Response get(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> Response.ok(find(c, id)));
    }

    /** {@code POST {customerId, items: [{productId, quantity}]}}. */
    public Response create(Request request) {
        JsonNode body = request.body();
        Validator v = new Validator(body, false);
        JsonNode customerNode = body.get("customerId");
        if (customerNode == null || !customerNode.canConvertToLong() || !customerNode.isIntegralNumber()) {
            v.error("customerId", "Customer is required");
        }
        JsonNode items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            v.error("items", "At least one item is required");
        } else {
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                if (item.get("productId") == null || !item.get("productId").isIntegralNumber()) {
                    v.error("items[" + i + "].productId", "Product is required");
                }
                if (item.get("quantity") == null || !item.get("quantity").isIntegralNumber()
                        || item.get("quantity").intValue() < 1) {
                    v.error("items[" + i + "].quantity", "Quantity must be at least 1");
                }
            }
        }
        v.validate();

        long customerId = customerNode.longValue();
        return Sql.transaction(database, c -> {
            Map<String, Object> customer = CustomerApi.find(c, customerId);
            if (!"ACTIVE".equals(customer.get("status"))) {
                throw new ApiException(409, "Customer " + customerId + " is inactive");
            }
            BigDecimal total = BigDecimal.ZERO;
            List<long[]> lines = new ArrayList<>();
            List<BigDecimal> prices = new ArrayList<>();
            for (JsonNode item : items) {
                long productId = item.get("productId").longValue();
                int quantity = item.get("quantity").intValue();
                Map<String, Object> product = ProductApi.find(c, productId);
                if (!(Boolean) product.get("active")) {
                    throw new ApiException(409, "Product " + product.get("sku") + " is no longer sold");
                }
                if ((Integer) product.get("stock") < quantity) {
                    throw new ApiException(409, "Insufficient stock for " + product.get("sku") + ": "
                            + product.get("stock") + " left, " + quantity + " requested");
                }
                BigDecimal price = (BigDecimal) product.get("price");
                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
                lines.add(new long[] {productId, quantity});
                prices.add(price);
                adjustStock(c, productId, -quantity);
            }
            long orderId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (customer_id, status, total) VALUES (?, 'PLACED', ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, customerId);
                ps.setBigDecimal(2, total);
                ps.executeUpdate();
                orderId = Sql.generatedId(ps);
            }
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
        String status = v.oneOf("status", "Status", TRANSITIONS.keySet(), true);
        v.validate();
        return Sql.transaction(database, c -> {
            String current = (String) find(c, id).get("status");
            if (!TRANSITIONS.get(current).contains(status)) {
                throw new ApiException(409, "Order " + id + " cannot go from " + current + " to " + status);
            }
            if ("CANCELLED".equals(status)) {
                restoreStock(c, id);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return Response.ok(find(c, id));
        });
    }

    /** {@code DELETE}: removes the order (test clean-up); a PLACED order gives its stock back. */
    public Response delete(Request request) {
        long id = request.pathId("id");
        return Sql.transaction(database, c -> {
            if ("PLACED".equals(find(c, id).get("status"))) {
                restoreStock(c, id);
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM orders WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return Response.noContent();
        });
    }

    private static void restoreStock(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id, quantity FROM order_items WHERE order_id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    adjustStock(c, rs.getLong(1), rs.getInt(2));
                }
            }
        }
    }

    private static void adjustStock(Connection c, long productId, int delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE products SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    static Map<String, Object> find(Connection c, long id) throws SQLException {
        Map<String, Object> order = new LinkedHashMap<>();
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
                    item.put("lineTotal", rs.getBigDecimal("unit_price").multiply(BigDecimal.valueOf(rs.getInt("quantity"))));
                    items.add(item);
                }
            }
        }
        order.put("items", items);
        return order;
    }
}
