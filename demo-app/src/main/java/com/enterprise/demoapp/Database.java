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

    private final String url;
    private final Connection keepAlive;
    private final Server tcpServer;

    public Database(String name, int tcpPort, boolean allowRemote) {
        this.url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        try {
            // Keeping one connection open keeps the in-memory database alive for the whole run
            this.keepAlive = DriverManager.getConnection(url, "sa", "");
            runScript(keepAlive, "/db/schema.sql");
            this.tcpServer = tcpPort > 0 ? startTcp(tcpPort, allowRemote) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot start database " + url, e);
        }
    }

    /** A new connection; callers close it (try-with-resources). */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static Server startTcp(int port, boolean allowRemote) throws SQLException {
        Server server = allowRemote
                ? Server.createTcpServer("-tcpPort", String.valueOf(port), "-tcpAllowOthers", "-ifExists")
                : Server.createTcpServer("-tcpPort", String.valueOf(port), "-ifExists");
        server.start();
        LOG.info("Database TCP server on port {}", port);
        return server;
    }

    private static void runScript(Connection connection, String resource) throws SQLException {
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
                String trimmed = part.lines().filter(l -> !l.trim().startsWith("--"))
                        .reduce("", (a, b) -> a + "\n" + b).trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    @Override
    public void close() {
        if (tcpServer != null) {
            tcpServer.stop();
        }
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException e) {
            LOG.debug("Database shutdown: {}", e.getMessage());
        }
    }
}
