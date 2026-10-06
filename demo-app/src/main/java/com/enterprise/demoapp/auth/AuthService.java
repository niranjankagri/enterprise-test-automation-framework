package com.enterprise.demoapp.auth;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.http.ApiException;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login and bearer tokens.
 *
 * <p>Demo-grade security, enough to test authentication and authorization: salted SHA-256
 * password hashes and random opaque tokens kept in memory for one hour. A production system would
 * use a slow password hash (bcrypt/Argon2) and signed or stored tokens.
 */
public final class AuthService {

    public static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final Database database;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthService(Database database) {
        this.database = database;
    }

    /** Creates a user with a salted password hash (used for the seeded demo accounts and the users API). */
    public long createUser(Connection connection, String username, String password, String fullName, String role)
            throws SQLException {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO app_users (username, password_hash, salt, full_name, role) VALUES (?, ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash(salt, password));
            ps.setString(3, salt);
            ps.setString(4, fullName);
            ps.setString(5, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** {@code POST /api/auth/login} with {@code {username, password}}. */
    public Response login(Request request) {
        JsonNode body = request.body();
        String username = text(body, "username");
        String password = text(body, "password");
        Map<String, String> missing = new LinkedHashMap<>();
        if (username == null) {
            missing.put("username", "Username is required");
        }
        if (password == null) {
            missing.put("password", "Password is required");
        }
        if (!missing.isEmpty()) {
            throw ApiException.validation(missing);
        }

        try (Connection c = database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT password_hash, salt, full_name, role FROM app_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                // Same message for unknown user and wrong password: do not reveal which usernames exist
                if (!rs.next() || !MessageDigest.isEqual(
                        rs.getString("password_hash").getBytes(StandardCharsets.UTF_8),
                        hash(rs.getString("salt"), password).getBytes(StandardCharsets.UTF_8))) {
                    throw new ApiException(401, "Invalid username or password");
                }
                String token = UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");
                Session session = new Session(username, rs.getString("full_name"), rs.getString("role"),
                        Instant.now().plus(TOKEN_LIFETIME));
                sessions.put(token, session);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("token", token);
                result.put("tokenType", "Bearer");
                result.put("expiresIn", TOKEN_LIFETIME.toSeconds());
                result.put("username", username);
                result.put("fullName", session.fullName());
                result.put("role", session.role());
                return Response.ok(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code POST /api/auth/logout}: the token stops working immediately. */
    public Response logout(Request request) {
        String header = request.header("Authorization");
        sessions.remove(header.substring(7).trim());
        return Response.noContent();
    }

    /** Router hook: valid, unexpired token → request is signed in. */
    public boolean authenticate(String token, Request request) {
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        request.signIn(session.username(), session.role());
        return true;
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A signed-in user. */
    record Session(String username, String fullName, String role, Instant expiresAt) {
    }
}
