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
import java.util.stream.Collectors;
import com.enterprise.automation.reporting.Report;
import com.enterprise.automation.reporting.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs SQL against the application's database and returns rows as maps.
 *
 * <p>Only parameterized statements: values are always bound with {@code ?}, never concatenated
 * into SQL, so test data can contain any character and SQL injection is impossible. Column
 * names in the returned maps are lower-case, whatever the database returns.
 *
 * <p>Queries can carry a business name ({@link #named(String)}, used by {@link ShopDatabase}):
 * the report step, the evidence and the log then say "customer by email" instead of only SQL.
 */
public final class QueryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExecutor.class);

    // Opens a fresh JDBC connection per query
    private final DatabaseConnection connection;
    // Business name of the queries ("customer by email"), or null for ad-hoc SQL
    private final String queryName;

    public QueryExecutor(DatabaseConnection connection) {
        this(connection, null);
    }

    private QueryExecutor(DatabaseConnection connection, String queryName) {
        this.connection = connection;
        this.queryName = queryName;
    }

    /** The same executor with a business name for its queries (report step, evidence, log, errors). */
    public QueryExecutor named(String name) {
        return new QueryExecutor(connection, name);
    }

    /**
     * All rows of {@code sql}; shown in the report as a step ("DB: customer by email") with the
     * query name, SQL, parameters, database, duration and the rows (secret columns masked).
     */
    public List<Map<String, Object>> queryForList(String sql, Object... params) {
        return Report.step(queryName != null ? "DB: " + queryName : "SQL: " + sql, () -> {
            // nanoTime: a monotonic clock, right for durations
            long start = System.nanoTime();
            List<Map<String, Object>> rows = runQuery(sql, params);
            long millis = (System.nanoTime() - start) / 1_000_000;
            LOG.debug("{} -> {} row(s) in {} ms", label(sql), rows.size(), millis);
            // The report shows what was asked, where, how long it took and what came back
            Report.attachText("Query result", describe(sql, params) + "Duration: " + millis + " ms\n"
                    + "Result: " + rows.size() + " row(s)\n" + rows.stream().map(QueryExecutor::masked)
                    .map(String::valueOf).collect(Collectors.joining("\n")));
            return rows;
        });
    }

    /** Query name, SQL, parameters and database as the first lines of the evidence (URL credentials masked). */
    private String describe(String sql, Object... params) {
        return (queryName != null ? "Query: " + queryName + "\n" : "") + "SQL: " + sql + "\nParameters: "
                + Arrays.toString(params) + "\nDatabase: " + SecretMasker.mask(connection.url()) + "\n";
    }

    /** The query's name, or its SQL when it has none: what log lines and errors call it. */
    private String label(String sql) {
        return queryName != null ? queryName : sql;
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
            throw failure("Query", sql, params, e);
        }
    }

    /** The single row of {@code sql}, empty if none; fails if there are several. */
    public Optional<Map<String, Object>> queryForOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryForList(sql, params);
        // Several rows where one was expected means a wrong query: fail instead of picking one
        if (rows.size() > 1) {
            throw new IllegalStateException("Expected at most one row but got " + rows.size() + ": " + label(sql));
        }
        return rows.stream().findFirst();
    }

    /** The first column of the single row, e.g. a {@code COUNT(*)}. */
    public Object queryForValue(String sql, Object... params) {
        return queryForOne(sql, params).map(row -> row.values().iterator().next())
                .orElseThrow(() -> new IllegalStateException("No row for " + label(sql)));
    }

    /** A {@code SELECT COUNT(*) ...} as a long (drivers return different Number types). */
    public long count(String sql, Object... params) {
        return ((Number) queryForValue(sql, params)).longValue();
    }

    /** INSERT/UPDATE/DELETE, e.g. for clean-up the API cannot do; returns the affected row count. */
    public int update(String sql, Object... params) {
        LOG.debug("SQL {} {}", sql, Arrays.toString(params));
        long start = System.nanoTime();
        try (Connection c = connection.open(); PreparedStatement ps = prepare(c, sql, params)) {
            int affected = ps.executeUpdate();
            LOG.debug("{} -> {} row(s) affected in {} ms", label(sql), affected, (System.nanoTime() - start) / 1_000_000);
            return affected;
        } catch (SQLException e) {
            throw failure("Update", sql, params, e);
        }
    }

    /** A failure that names the query, the database (credentials masked), the SQL and the parameters. */
    private IllegalStateException failure(String kind, String sql, Object[] params, SQLException cause) {
        return new IllegalStateException(kind + " '" + label(sql) + "' failed on " + SecretMasker.mask(connection.url())
                + ": " + sql + " " + Arrays.toString(params), cause);
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
