package com.enterprise.automation.tests.base;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.DatabaseConfig;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.demoapp.DemoApp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Starts the application under test before the suite when the environment asks for it
 * ({@code app.autostart=true}, i.e. {@code local}) and stops it when the JVM exits.
 *
 * <p>Other environments ({@code qa}, {@code staging}) point at an application that is already
 * running, so nothing is started there. Registered in the TestNG suite files.
 */
public class DemoAppLifecycle implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(DemoAppLifecycle.class);
    // One application per JVM, shared by all suites of the run
    private static DemoApp app;

    @Override
    public void onStart(ISuite suite) {
        startIfNeeded();
    }

    // synchronized + null check: started exactly once, even with several suites
    private static synchronized void startIfNeeded() {
        TestConfig config = ConfigManager.config();
        // qa/staging: the application is already running elsewhere
        if (!config.appAutostart() || app != null) {
            return;
        }
        // The HTTP port comes from base.url (8080 for local)
        int port = config.baseUrl().getPort();
        DemoApp.Options options = DemoApp.Options.fromEnvironment().withPort(port);
        // The database port comes from db.url, so the application and the tests always agree on it
        Matcher tcp = TCP_PORT.matcher(config.database().map(DatabaseConfig::url).orElse(""));
        if (tcp.find()) {
            options = options.withDbTcpPort(Integer.parseInt(tcp.group(1)));
        }
        LOG.info("Starting the application under test on port {} (database port {})", port, options.dbTcpPort());
        app = DemoApp.start(options);
        // Stopped when the test JVM exits, after all suites and their clean-ups
        Runtime.getRuntime().addShutdownHook(new Thread(app::close, "demo-app-shutdown"));
    }

    // The port in "jdbc:h2:tcp://localhost:9092/mem:shop"
    private static final Pattern TCP_PORT = Pattern.compile("tcp://[^:/]+:(\\d+)/");
}
