package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.auth.AuthService;
import com.enterprise.demoapp.http.ApiException;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code /api/users}: the back-office accounts. Everything except {@code /me} needs the ADMIN role. */
public final class UserApi {

    // The two roles the application knows
    private static final Set<String> ROLES = Set.of("ADMIN", "VIEWER");
    // Never select password_hash/salt: users are returned without any secret
    private static final String COLUMNS = "id, username, full_name, role, created_at";

    private final Database database;
    // Creates accounts with salted password hashes
    private final AuthService auth;

    public UserApi(Database database, AuthService auth) {
        this.database = database;
        this.auth = auth;
    }

    /** {@code GET /api/users/me}: the signed-in user. */
    public Response me(Request request) {
        return Sql.run(database, c -> {
            // The router signed the request in, so username() is known and the row exists
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM app_users WHERE username = ?")) {
                ps.setString(1, request.username());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return Response.ok(map(rs));
                }
            }
        });
    }

    /** {@code GET /api/users}: every account, oldest first. */
    public Response list(Request request) {
        return Sql.run(database, c -> {
            List<Map<String, Object>> users = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM app_users ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(map(rs));
                }
            }
            return Response.ok(users);
        });
    }

    /** {@code GET /api/users/{id}}; 404 if unknown. */
    public Response get(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> Response.ok(find(c, id)));
    }

    /** {@code POST {username, password, fullName, role}}; the password is never returned. */
    public Response create(Request request) {
        Validator v = new Validator(request.body(), false);
        String username = v.text("username", "Username", 50, true);
        String password = v.text("password", "Password", 100, true);
        String fullName = v.text("fullName", "Full name", 100, true);
        String role = v.oneOf("role", "Role", ROLES, true);
        // Minimum password length
        if (password != null && password.length() < 8) {
            v.error("password", "Password must be at least 8 characters");
        }
        v.validate();
        return Sql.run(database, c -> {
            // Usernames are unique
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM app_users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ApiException(409, "Username " + username + " is already taken",
                                Map.of("username", "Username is already taken"));
                    }
                }
            }
            // AuthService salts and hashes the password
            long id = auth.createUser(c, username, password, fullName, role);
            return Response.created(find(c, id), "/api/users/" + id);
        });
    }

    /** {@code PATCH {fullName?, role?}}. */
    public Response update(Request request) {
        long id = request.pathId("id");
        Validator v = new Validator(request.body(), true);
        String fullName = v.text("fullName", "Full name", 100, false);
        String role = v.oneOf("role", "Role", ROLES, false);
        v.validate();
        return Sql.run(database, c -> {
            // Fields not in the body keep their current values
            Map<String, Object> current = find(c, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE app_users SET full_name = ?, role = ? WHERE id = ?")) {
                ps.setString(1, fullName != null ? fullName : (String) current.get("fullName"));
                ps.setString(2, role != null ? role : (String) current.get("role"));
                ps.setLong(3, id);
                ps.executeUpdate();
            }
            return Response.ok(find(c, id));
        });
    }

    /** {@code DELETE}: an admin cannot delete their own account. */
    public Response delete(Request request) {
        long id = request.pathId("id");
        return Sql.run(database, c -> {
            // Prevents locking yourself out
            if (find(c, id).get("username").equals(request.username())) {
                throw new ApiException(409, "You cannot delete your own account");
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM app_users WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return Response.noContent();
        });
    }

    /** One account; 404 if it does not exist. */
    private static Map<String, Object> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM app_users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("User", id);
                }
                return map(rs);
            }
        }
    }

    /** Database row -> JSON field names (no secrets). */
    private static Map<String, Object> map(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("username", rs.getString("username"));
        m.put("fullName", rs.getString("full_name"));
        m.put("role", rs.getString("role"));
        m.put("createdAt", rs.getTimestamp("created_at").toInstant());
        return m;
    }
}
