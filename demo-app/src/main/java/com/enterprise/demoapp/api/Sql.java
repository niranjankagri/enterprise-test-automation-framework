package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC plumbing shared by the API classes. */
final class Sql {

    private Sql() {
    }

    /** Work on one connection. */
    @FunctionalInterface
    interface Work<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Runs {@code work} on a fresh connection; SQL errors become 500s. */
    static <T> T run(Database database, Work<T> work) {
        try (Connection c = database.connect()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    /** Runs {@code work} in one transaction: all of it is committed, or none of it. */
    static <T> T transaction(Database database, Work<T> work) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    static long count(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
