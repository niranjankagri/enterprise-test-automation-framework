package com.enterprise.automation.db;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import org.testng.SkipException;

/**
 * Opens JDBC connections to the application's database, from the run configuration.
 *
 * <p>A connection per unit of work (opened and closed by {@link QueryExecutor}) instead of one
 * shared connection: simple, and safe for parallel tests. Test volumes do not need a pool.
 */
public final class DatabaseConnection {

    // URL, user and password of the environment's database
    private final DatabaseConfig config;

    public DatabaseConnection(DatabaseConfig config) {
        this.config = config;
    }

    /**
     * The database of the configured environment. Environments without database access
     * ({@code db.url} not set) skip the calling test with a clear reason instead of failing it.
     */
    public static DatabaseConnection fromConfig() {
        Optional<DatabaseConfig> database = ConfigManager.config().database();
        // SkipException: TestNG reports the test (or class, from @BeforeClass) as skipped with this reason
        if (database.isEmpty()) {
            throw new SkipException("No database access in environment '" + ConfigManager.config().environment()
                    + "' (set db.url or DB_URL to run database checks)");
        }
        return new DatabaseConnection(database.get());
    }

    /** A new connection (the JDBC driver on the classpath is found by the URL prefix). */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.username(), config.password());
    }

    /** The JDBC URL (no secrets in it). */
    public String url() {
        return config.url();
    }
}
