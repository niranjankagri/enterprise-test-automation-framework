package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.http.ApiException;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
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

/** {@code /api/products}: list/search, get, create, replace, update, delete (soft). */
public final class ProductApi {

    private static final String COLUMNS = "id, sku, name, category, price, stock, active";

    private final Database database;

    public ProductApi(Database database) {
        this.database = database;
    }

    /** {@code GET /api/products?search=&category=}; only active products unless {@code includeInactive=true}. */
    public Response list(Request request) {
        String search = request.query("search");
        String category = request.query("category");
        boolean includeInactive = "true".equalsIgnoreCase(request.query("includeInactive"));
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM products WHERE 1 = 1");
        List<String> args = new ArrayList<>();
        if (!includeInactive) {
            sql.append(" AND active");
        }
        if (search != null) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(sku) LIKE ?)");
            args.add("%" + search.toLowerCase() + "%");
            args.add("%" + search.toLowerCase() + "%");
        }
        if (category != null) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        sql.append(" ORDER BY id");
        return Sql.run(database, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) {
                    ps.setString(i + 1, args.get(i));
                }
                List<Map<String, Object>> products = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        products.add(map(rs));
                    }
                }
                return Response.ok(products);
            }
        });
    }

    public Response get(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> Response.ok(find(c, id)));
    }

    public Response create(Request request) {
        Fields f = read(request, false);
        return Sql.run(database, c -> {
            ensureSkuFree(c, f.sku, 0);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (sku, name, category, price, stock) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, f.sku);
                ps.setString(2, f.name);
                ps.setString(3, f.category);
                ps.setBigDecimal(4, f.price);
                ps.setInt(5, f.stock);
                ps.executeUpdate();
                long id = Sql.generatedId(ps);
                return Response.created(find(c, id), "/api/products/" + id);
            }
        });
    }

    public Response replace(Request request) {
        long id = request.pathId("id");
        Fields f = read(request, false);
        return Sql.run(database, c -> {
            find(c, id);
            ensureSkuFree(c, f.sku, id);
            save(c, id, f.sku, f.name, f.category, f.price, f.stock);
            return Response.ok(find(c, id));
        });
    }

    public Response update(Request request) {
        long id = request.pathId("id");
        Fields f = read(request, true);
        return Sql.run(database, c -> {
            Map<String, Object> current = find(c, id);
            if (f.sku != null) {
                ensureSkuFree(c, f.sku, id);
            }
            save(c, id,
                    f.sku != null ? f.sku : (String) current.get("sku"),
                    f.name != null ? f.name : (String) current.get("name"),
                    f.category != null ? f.category : (String) current.get("category"),
                    f.price != null ? f.price : (BigDecimal) current.get("price"),
                    f.stock != null ? f.stock : (Integer) current.get("stock"));
            return Response.ok(find(c, id));
        });
    }

    /** {@code DELETE}: products referenced by orders are kept for history, so this deactivates them. */
    public Response delete(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> {
            find(c, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE products SET active = FALSE WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return Response.noContent();
        });
    }

    static Map<String, Object> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM products WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Product", id);
                }
                return map(rs);
            }
        }
    }

    private static void save(Connection c, long id, String sku, String name, String category, BigDecimal price,
                             int stock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE products SET sku = ?, name = ?, category = ?, price = ?, stock = ? WHERE id = ?")) {
            ps.setString(1, sku);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setBigDecimal(4, price);
            ps.setInt(5, stock);
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    private static void ensureSkuFree(Connection c, String sku, long ownId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM products WHERE sku = ? AND id <> ?")) {
            ps.setString(1, sku);
            ps.setLong(2, ownId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new ApiException(409, "A product with SKU " + sku + " already exists",
                            Map.of("sku", "SKU is already in use"));
                }
            }
        }
    }

    private static Fields read(Request request, boolean partial) {
        Validator v = new Validator(request.body(), partial);
        Fields f = new Fields();
        f.sku = v.text("sku", "SKU", 30, true);
        f.name = v.text("name", "Name", 100, true);
        f.category = v.text("category", "Category", 50, true);
        f.price = v.positiveDecimal("price", "Price", true);
        f.stock = v.nonNegativeInt("stock", "Stock", true);
        v.validate();
        return f;
    }

    private static Map<String, Object> map(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("sku", rs.getString("sku"));
        m.put("name", rs.getString("name"));
        m.put("category", rs.getString("category"));
        m.put("price", rs.getBigDecimal("price"));
        m.put("stock", rs.getInt("stock"));
        m.put("active", rs.getBoolean("active"));
        return m;
    }

    private static final class Fields {
        String sku;
        String name;
        String category;
        BigDecimal price;
        Integer stock;
    }
}
