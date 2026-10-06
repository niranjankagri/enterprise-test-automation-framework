package com.enterprise.automation.tests.base;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.demoapp.DemoApp;
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
    private static DemoApp app;

    @Override
    public void onStart(ISuite suite) {
        startIfNeeded();
    }

    private static synchronized void startIfNeeded() {
        TestConfig config = ConfigManager.config();
        if (!config.appAutostart() || app != null) {
            return;
        }
        int port = config.baseUrl().getPort();
        LOG.info("Starting the application under test on port {}", port);
        app = DemoApp.start(DemoApp.Options.fromEnvironment().withPort(port));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close, "demo-app-shutdown"));
    }
}
