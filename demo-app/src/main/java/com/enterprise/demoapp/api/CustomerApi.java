package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.http.ApiException;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
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

/** {@code /api/customers}: list/search, get, create, replace, update, delete. */
public final class CustomerApi {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final String COLUMNS = "id, first_name, last_name, email, phone, city, status, created_at";

    private final Database database;

    public CustomerApi(Database database) {
        this.database = database;
    }

    /** {@code GET /api/customers?search=} matches name, email or city (case-insensitive). */
    public Response list(Request request) {
        String search = request.query("search");
        String sql = "SELECT " + COLUMNS + " FROM customers"
                + (search == null ? "" : " WHERE LOWER(CONCAT(first_name, ' ', last_name, ' ', email, ' ',"
                        + " COALESCE(city, ''))) LIKE ?")
                + " ORDER BY id";
        return Sql.run(database, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (search != null) {
                    ps.setString(1, "%" + search.toLowerCase() + "%");
                }
                List<Map<String, Object>> customers = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        customers.add(map(rs));
                    }
                }
                return Response.ok(customers);
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
            ensureEmailFree(c, f.email, 0);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO customers (first_name, last_name, email, phone, city, status) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, f.firstName);
                ps.setString(2, f.lastName);
                ps.setString(3, f.email);
                ps.setString(4, f.phone);
                ps.setString(5, f.city);
                ps.setString(6, f.status == null ? "ACTIVE" : f.status);
                ps.executeUpdate();
                long id = Sql.generatedId(ps);
                return Response.created(find(c, id), "/api/customers/" + id);
            }
        });
    }

    /** {@code PUT}: full replacement, same rules as create. */
    public Response replace(Request request) {
        long id = request.pathId("id");
        Fields f = read(request, false);
        return Sql.run(database, c -> {
            find(c, id);
            ensureEmailFree(c, f.email, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET first_name = ?, last_name = ?,"
                    + " email = ?, phone = ?, city = ?, status = ? WHERE id = ?")) {
                ps.setString(1, f.firstName);
                ps.setString(2, f.lastName);
                ps.setString(3, f.email);
                ps.setString(4, f.phone);
                ps.setString(5, f.city);
                ps.setString(6, f.status == null ? "ACTIVE" : f.status);
                ps.setLong(7, id);
                ps.executeUpdate();
            }
            return Response.ok(find(c, id));
        });
    }

    /** {@code PATCH}: only the fields present in the body change. */
    public Response update(Request request) {
        long id = request.pathId("id");
        Fields f = read(request, true);
        return Sql.run(database, c -> {
            Map<String, Object> current = find(c, id);
            if (f.email != null) {
                ensureEmailFree(c, f.email, id);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET first_name = ?, last_name = ?,"
                    + " email = ?, phone = ?, city = ?, status = ? WHERE id = ?")) {
                ps.setString(1, f.firstName != null ? f.firstName : (String) current.get("firstName"));
                ps.setString(2, f.lastName != null ? f.lastName : (String) current.get("lastName"));
                ps.setString(3, f.email != null ? f.email : (String) current.get("email"));
                ps.setString(4, f.hasPhone ? f.phone : (String) current.get("phone"));
                ps.setString(5, f.hasCity ? f.city : (String) current.get("city"));
                ps.setString(6, f.status != null ? f.status : (String) current.get("status"));
                ps.setLong(7, id);
                ps.executeUpdate();
            }
            return Response.ok(find(c, id));
        });
    }

    /** {@code DELETE}: refused with 409 while the customer has orders. */
    public Response delete(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> {
            find(c, id);
            if (Sql.count(c, "SELECT COUNT(*) FROM orders WHERE customer_id = ?", id) > 0) {
                throw new ApiException(409, "Customer " + id + " has orders and cannot be deleted");
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM customers WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return Response.noContent();
        });
    }

    static Map<String, Object> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM customers WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Customer", id);
                }
                return map(rs);
            }
        }
    }

    private static void ensureEmailFree(Connection c, String email, long ownId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM customers WHERE LOWER(email) = ? AND id <> ?")) {
            ps.setString(1, email.toLowerCase());
            ps.setLong(2, ownId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new ApiException(409, "A customer with email " + email + " already exists",
                            Map.of("email", "Email is already in use"));
                }
            }
        }
    }

    private static Fields read(Request request, boolean partial) {
        Validator v = new Validator(request.body(), partial);
        Fields f = new Fields();
        f.firstName = v.text("firstName", "First name", 50, true);
        f.lastName = v.text("lastName", "Last name", 50, true);
        f.email = v.email("email", true);
        f.hasPhone = v.has("phone");
        f.phone = v.phone("phone");
        f.hasCity = v.has("city");
        f.city = v.text("city", "City", 60, false);
        f.status = v.oneOf("status", "Status", STATUSES, false);
        v.validate();
        return f;
    }

    private static Map<String, Object> map(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("firstName", rs.getString("first_name"));
        m.put("lastName", rs.getString("last_name"));
        m.put("email", rs.getString("email"));
        m.put("phone", rs.getString("phone"));
        m.put("city", rs.getString("city"));
        m.put("status", rs.getString("status"));
        m.put("createdAt", rs.getTimestamp("created_at").toInstant());
        return m;
    }

    private static final class Fields {
        String firstName;
        String lastName;
        String email;
        String phone;
        String city;
        String status;
        boolean hasPhone;
        boolean hasCity;
    }
}
