package com.enterprise.demoapp;

import com.enterprise.demoapp.api.CustomerApi;
import com.enterprise.demoapp.api.OrderApi;
import com.enterprise.demoapp.api.ProductApi;
import com.enterprise.demoapp.api.StatsApi;
import com.enterprise.demoapp.api.UserApi;
import com.enterprise.demoapp.auth.AuthService;
import com.enterprise.demoapp.http.Router;
import com.enterprise.demoapp.http.Router.Access;
import com.enterprise.demoapp.http.StaticFiles;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ShopEase Admin: the application under test.
 *
 * <p>Run stand-alone with {@code java -jar demo-app.jar [--port 8081] [--db-port 9093]}, or start
 * it in-process with {@link #start(Options)} (the test suite does this for the {@code local}
 * environment). Settings can also come from environment variables, which is how Docker sets them.
 */
public final class DemoApp implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DemoApp.class);

    /**
     * Start-up settings.
     *
     * @param port           HTTP port of UI and API
     * @param dbName         name of the in-memory database
     * @param dbTcpPort      H2 TCP port for JDBC access from tests ({@code 0} = none)
     * @param dbAllowRemote  accept database connections from other hosts (containers)
     * @param latencyMillis  artificial delay per API call, to imitate a real backend
     * @param adminPassword  password of the seeded {@code admin} account
     * @param viewerPassword password of the seeded read-only {@code viewer} account
     */
    public record Options(int port, String dbName, int dbTcpPort, boolean dbAllowRemote, long latencyMillis,
                          String adminPassword, String viewerPassword) {

        /** Defaults, overridable by environment variables (PORT, DB_TCP_PORT, ...). */
        public static Options fromEnvironment() {
            return new Options(
                    intEnv("PORT", 8080),
                    env("DB_NAME", "shop"),
                    intEnv("DB_TCP_PORT", 9092),
                    Boolean.parseBoolean(env("DB_ALLOW_REMOTE", "false")),
                    intEnv("LATENCY_MS", 100),
                    env("ADMIN_PASSWORD", "Admin@12345"),
                    env("VIEWER_PASSWORD", "Viewer@12345"));
        }

        /** Same options on another HTTP port (records are immutable: returns a copy). */
        public Options withPort(int newPort) {
            return new Options(newPort, dbName, dbTcpPort, dbAllowRemote, latencyMillis, adminPassword, viewerPassword);
        }

        /** Same options with another database TCP port. */
        public Options withDbTcpPort(int newPort) {
            return new Options(port, dbName, newPort, dbAllowRemote, latencyMillis, adminPassword, viewerPassword);
        }

        /** Environment variable, or {@code fallback} when unset or blank. */
        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }

        /** Numeric environment variable with a default. */
        private static int intEnv(String name, int fallback) {
            return Integer.parseInt(env(name, String.valueOf(fallback)));
        }
    }

    // The running HTTP server (UI + API)
    private final HttpServer server;
    // Its worker threads, shut down on close
    private final ExecutorService executor;
    // The in-memory database (and its TCP server)
    private final Database database;

    private DemoApp(HttpServer server, ExecutorService executor, Database database) {
        this.server = server;
        this.executor = executor;
        this.database = database;
    }

    public static void main(String[] args) {
        // Defaults and environment variables first; command-line options override them
        Options options = Options.fromEnvironment();
        // Options come in pairs: --name value
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--port" -> options = options.withPort(Integer.parseInt(args[i + 1]));
                case "--db-port" -> options = options.withDbTcpPort(Integer.parseInt(args[i + 1]));
                default -> throw new IllegalArgumentException("Unknown option " + args[i]
                        + ". Usage: java -jar demo-app.jar [--port 8080] [--db-port 9092]");
            }
        }
        DemoApp app = start(options);
        // Ctrl+C / docker stop: shut the server and the database down cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
    }

    /** Starts database, API and UI; returns once the server accepts requests. */
    public static DemoApp start(Options options) {
        // 1. Database with schema and seed data (+ TCP server for tests)
        Database database = new Database(options.dbName(), options.dbTcpPort(), options.dbAllowRemote());
        // 2. Authentication and the two demo accounts
        AuthService auth = new AuthService(database);
        seedUsers(database, auth, options);

        // 3. One handler object per resource
        CustomerApi customers = new CustomerApi(database);
        ProductApi products = new ProductApi(database);
        OrderApi orders = new OrderApi(database);
        UserApi users = new UserApi(database, auth);
        StatsApi stats = new StatsApi(database);

        // 4. The REST routes: method, path, who may call it, which handler runs
        Router api = new Router(auth::authenticate, options.latencyMillis())
                .route("POST", "/api/auth/login", Access.PUBLIC, auth::login)
                .route("POST", "/api/auth/logout", Access.USER, auth::logout)
                .route("GET", "/api/stats", Access.USER, stats::get)
                .route("GET", "/api/users/me", Access.USER, users::me)
                .route("GET", "/api/users", Access.ADMIN, users::list)
                .route("POST", "/api/users", Access.ADMIN, users::create)
                .route("GET", "/api/users/{id}", Access.ADMIN, users::get)
                .route("PATCH", "/api/users/{id}", Access.ADMIN, users::update)
                .route("DELETE", "/api/users/{id}", Access.ADMIN, users::delete)
                .route("GET", "/api/customers", Access.USER, customers::list)
                .route("POST", "/api/customers", Access.ADMIN, customers::create)
                .route("GET", "/api/customers/{id}", Access.USER, customers::get)
                .route("PUT", "/api/customers/{id}", Access.ADMIN, customers::replace)
                .route("PATCH", "/api/customers/{id}", Access.ADMIN, customers::update)
                .route("DELETE", "/api/customers/{id}", Access.ADMIN, customers::delete)
                .route("GET", "/api/products", Access.USER, products::list)
                .route("POST", "/api/products", Access.ADMIN, products::create)
                .route("GET", "/api/products/{id}", Access.USER, products::get)
                .route("PUT", "/api/products/{id}", Access.ADMIN, products::replace)
                .route("PATCH", "/api/products/{id}", Access.ADMIN, products::update)
                .route("DELETE", "/api/products/{id}", Access.ADMIN, products::delete)
                .route("GET", "/api/orders", Access.USER, orders::list)
                .route("POST", "/api/orders", Access.ADMIN, orders::create)
                .route("GET", "/api/orders/{id}", Access.USER, orders::get)
                .route("PATCH", "/api/orders/{id}", Access.ADMIN, orders::updateStatus)
                .route("DELETE", "/api/orders/{id}", Access.ADMIN, orders::delete);

        // 5. The HTTP server: /api goes to the router, everything else is the static UI
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(options.port()), 0);
            server.createContext("/api", api);
            server.createContext("/", new StaticFiles());
            // 32 worker threads: enough for parallel test runs with several browsers
            ExecutorService executor = Executors.newFixedThreadPool(32);
            server.setExecutor(executor);
            server.start();
            LOG.info("ShopEase Admin running on http://localhost:{} (database TCP port {})",
                    options.port(), options.dbTcpPort());
            return new DemoApp(server, executor, database);
        } catch (IOException e) {
            // Port already in use, typically: release the database before failing
            database.close();
            throw new UncheckedIOException("Cannot start the demo app on port " + options.port(), e);
        }
    }

    /** Creates the demo accounts: admin (full access) and viewer (read-only). */
    private static void seedUsers(Database database, AuthService auth, Options options) {
        try (Connection c = database.connect()) {
            auth.createUser(c, "admin", options.adminPassword(), "Alex Admin", "ADMIN");
            auth.createUser(c, "viewer", options.viewerPassword(), "Vera Viewer", "VIEWER");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the demo accounts", e);
        }
    }

    /** The port the server actually listens on. */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        // Stop at once (0 s grace), then the worker threads, then the database
        server.stop(0);
        executor.shutdownNow();
        database.close();
        LOG.info("ShopEase Admin stopped");
    }
}
