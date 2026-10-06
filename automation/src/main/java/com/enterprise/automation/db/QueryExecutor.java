package com.enterprise.automation.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.enterprise.automation.reporting.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs SQL against the application's database and returns rows as maps.
 *
 * <p>Only parameterized statements: values are always bound with {@code ?}, never concatenated
 * into SQL, so test data can contain any character and SQL injection is impossible. Column
 * names in the returned maps are lower-case, whatever the database returns.
 */
public final class QueryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExecutor.class);

    // Opens a fresh JDBC connection per query
    private final DatabaseConnection connection;

    public QueryExecutor(DatabaseConnection connection) {
        this.connection = connection;
    }

    /** All rows of {@code sql}; shown in the report as a step with the parameters and the rows. */
    public List<Map<String, Object>> queryForList(String sql, Object... params) {
        return Report.step("SQL: " + sql, () -> {
            List<Map<String, Object>> rows = runQuery(sql, params);
            // The report shows what was asked and what came back (secret columns masked)
            Report.attachText("Query result", "Parameters: " + Arrays.toString(params) + "\n"
                    + rows.size() + " row(s)\n" + rows.stream().map(QueryExecutor::masked).map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining("\n")));
            return rows;
        });
    }

    /** Hashes and salts are never written to a report. */
    private static Map<String, Object> masked(Map<String, Object> row) {
        // A copy: the caller still gets the real values to assert on
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.replaceAll((column, value) -> column.contains("password") || column.equals("salt") ? "****" : value);
        return copy;
    }

    /** Runs the query and turns every row into a column -> value map. */
    private List<Map<String, Object>> runQuery(String sql, Object... params) {
        LOG.debug("SQL {} {}", sql, Arrays.toString(params));
        // Connection, statement and result set are all closed, also on errors
        try (Connection c = connection.open(); PreparedStatement ps = prepare(c, sql, params);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                // JDBC columns are numbered from 1; labels honour "AS" aliases; lower-cased because H2 upper-cases them
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql + " " + Arrays.toString(params), e);
        }
    }

    /** The single row of {@code sql}, empty if none; fails if there are several. */
    public Optional<Map<String, Object>> queryForOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryForList(sql, params);
        // Several rows where one was expected means a wrong query: fail instead of picking one
        if (rows.size() > 1) {
            throw new IllegalStateException("Expected at most one row but got " + rows.size() + ": " + sql);
        }
        return rows.stream().findFirst();
    }

    /** The first column of the single row, e.g. a {@code COUNT(*)}. */
    public Object queryForValue(String sql, Object... params) {
        return queryForOne(sql, params).map(row -> row.values().iterator().next())
                .orElseThrow(() -> new IllegalStateException("No row for " + sql));
    }

    /** A {@code SELECT COUNT(*) ...} as a long (drivers return different Number types). */
    public long count(String sql, Object... params) {
        return ((Number) queryForValue(sql, params)).longValue();
    }

    /** INSERT/UPDATE/DELETE, e.g. for clean-up the API cannot do; returns the affected row count. */
    public int update(String sql, Object... params) {
        LOG.debug("SQL {} {}", sql, Arrays.toString(params));
        try (Connection c = connection.open(); PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Update failed: " + sql + " " + Arrays.toString(params), e);
        }
    }

    /** Prepares {@code sql} and binds each parameter to its {@code ?} placeholder. */
    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            // Placeholders are numbered from 1
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        } catch (SQLException e) {
            ps.close(); // not yet owned by a try-with-resources
            throw e;
        }
    }
}
