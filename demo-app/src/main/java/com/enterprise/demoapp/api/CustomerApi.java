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

    // Allowed values of the status field
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    // Columns read for every customer response
    private static final String COLUMNS = "id, first_name, last_name, email, phone, city, status, created_at";

    private final Database database;

    public CustomerApi(Database database) {
        this.database = database;
    }

    /** {@code GET /api/customers?search=} matches name, email or city (case-insensitive). */
    public Response list(Request request) {
        String search = request.query("search");
        // With a search term: one LIKE over "first last email city", all lower-case
        String sql = "SELECT " + COLUMNS + " FROM customers"
                + (search == null ? "" : " WHERE LOWER(CONCAT(first_name, ' ', last_name, ' ', email, ' ',"
                        + " COALESCE(city, ''))) LIKE ?")
                + " ORDER BY id";
        return Sql.run(database, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                // The term is a bound parameter (never concatenated into the SQL)
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

    /** {@code GET /api/customers/{id}}; 404 if unknown. */
    public Response get(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> Response.ok(find(c, id)));
    }

    /** {@code POST /api/customers}: validates, rejects duplicate emails (409), returns 201. */
    public Response create(Request request) {
        // All field checks first: one 400 with every problem
        Fields f = read(request, false);
        return Sql.run(database, c -> {
            // Email is the natural key: no two customers share one
            ensureEmailFree(c, f.email, 0);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO customers (first_name, last_name, email, phone, city, status) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, f.firstName);
                ps.setString(2, f.lastName);
                ps.setString(3, f.email);
                ps.setString(4, f.phone);
                ps.setString(5, f.city);
                // New customers are active unless the request says otherwise
                ps.setString(6, f.status == null ? "ACTIVE" : f.status);
                ps.executeUpdate();
                long id = Sql.generatedId(ps);
                // 201 with the stored customer and its URL
                return Response.created(find(c, id), "/api/customers/" + id);
            }
        });
    }

    /** {@code PUT}: full replacement, same rules as create. */
    public Response replace(Request request) {
        long id = request.pathId("id");
        Fields f = read(request, false);
        return Sql.run(database, c -> {
            // 404 if the customer does not exist
            find(c, id);
            // The email may stay the same (own id excluded) but not clash with another customer
            ensureEmailFree(c, f.email, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET first_name = ?, last_name = ?,"
                    + " email = ?, phone = ?, city = ?, status = ? WHERE id = ?")) {
                // Every column is replaced: omitted optional fields become NULL
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
        // partial = true: absent fields are not "required" errors
        Fields f = read(request, true);
        return Sql.run(database, c -> {
            // Current values fill in the fields the body does not mention
            Map<String, Object> current = find(c, id);
            if (f.email != null) {
                ensureEmailFree(c, f.email, id);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET first_name = ?, last_name = ?,"
                    + " email = ?, phone = ?, city = ?, status = ? WHERE id = ?")) {
                ps.setString(1, f.firstName != null ? f.firstName : (String) current.get("firstName"));
                ps.setString(2, f.lastName != null ? f.lastName : (String) current.get("lastName"));
                ps.setString(3, f.email != null ? f.email : (String) current.get("email"));
                // phone/city: "present in the body" (even as an empty value) is what decides
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
            // Orders reference their customer: deleting would break order history
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

    /** One customer as a response map; 404 if it does not exist. Also used by {@link OrderApi}. */
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

    /** 409 when another customer (not {@code ownId}) already uses {@code email} (case-insensitive). */
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

    /** Reads and validates the body; throws one 400 listing every invalid field. */
    private static Fields read(Request request, boolean partial) {
        Validator v = new Validator(request.body(), partial);
        Fields f = new Fields();
        f.firstName = v.text("firstName", "First name", 50, true);
        f.lastName = v.text("lastName", "Last name", 50, true);
        f.email = v.email("email", true);
        // has(...) is recorded separately: for PATCH, "phone": null means "clear it"
        f.hasPhone = v.has("phone");
        f.phone = v.phone("phone");
        f.hasCity = v.has("city");
        f.city = v.text("city", "City", 60, false);
        f.status = v.oneOf("status", "Status", STATUSES, false);
        v.validate();
        return f;
    }

    /** Database row -> JSON field names (camelCase). */
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

    /** Validated request fields; {@code null} means "not given". */
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
