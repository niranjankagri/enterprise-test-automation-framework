package com.enterprise.demoapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application's H2 database.
 *
 * <p>An in-memory database, recreated from {@code db/schema.sql} on every start. When a TCP port
 * is configured, an H2 TCP server is started as well, so tests (another JVM, a container) can
 * validate the data over JDBC: {@code jdbc:h2:tcp://localhost:<port>/mem:<name>}.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Database.class);

    // In-process JDBC URL; DB_CLOSE_DELAY=-1 keeps the data while any connection is open
    private final String url;
    // One connection held for the app's lifetime so the in-memory database is never dropped
    private final Connection keepAlive;
    // Optional TCP server through which tests read the same database (null when disabled)
    private final Server tcpServer;

    public Database(String name, int tcpPort, boolean allowRemote) {
        this.url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        try {
            // Keeping one connection open keeps the in-memory database alive for the whole run
            this.keepAlive = DriverManager.getConnection(url, "sa", "");
            // Create tables and seed data (products, customers)
            runScript(keepAlive, "/db/schema.sql");
            // Port 0 = no TCP access (the database is then only visible inside this JVM)
            this.tcpServer = tcpPort > 0 ? startTcp(tcpPort, allowRemote) : null;
        } catch (SQLException e) {
            // The app cannot work without its database: fail start-up with the URL in the message
            throw new IllegalStateException("Cannot start database " + url, e);
        }
    }

    /** A new connection; callers close it (try-with-resources). */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    /** Starts H2's TCP server so other processes can connect with {@code jdbc:h2:tcp://...}. */
    private static Server startTcp(int port, boolean allowRemote) throws SQLException {
        // -tcpAllowOthers: accept connections from other hosts (needed between Docker containers)
        // -ifExists: clients may only open databases that already exist (no accidental new ones)
        Server server = allowRemote
                ? Server.createTcpServer("-tcpPort", String.valueOf(port), "-tcpAllowOthers", "-ifExists")
                : Server.createTcpServer("-tcpPort", String.valueOf(port), "-ifExists");
        server.start();
        LOG.info("Database TCP server on port {}", port);
        return server;
    }

    /** Runs a SQL script from the classpath, statement by statement ({@code ;}-separated). */
    private static void runScript(Connection connection, String resource) throws SQLException {
        // Read the whole script as UTF-8 text
        String sql;
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                // Drop "--" comment lines, keep the SQL itself
                String trimmed = part.lines().filter(l -> !l.trim().startsWith("--"))
                        .reduce("", (a, b) -> a + "\n" + b).trim();
                // Skip what is left after the last ";" (empty)
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    @Override
    public void close() {
        // Stop accepting TCP connections first
        if (tcpServer != null) {
            tcpServer.stop();
        }
        // Then drop the in-memory database
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException e) {
            // Already closed is fine at shutdown; note it at DEBUG only
            LOG.debug("Database shutdown: {}", e.getMessage());
        }
    }
}
