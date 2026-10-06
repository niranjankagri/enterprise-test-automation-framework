package com.enterprise.automation.listeners;

import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.ExecutionSettings;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;

/**
 * Applies the configured parallel mode and thread count to every suite before it runs.
 *
 * <p>Suite XML files describe WHAT runs (groups, packages); how it runs comes from configuration,
 * so the same suite runs serially on a laptop ({@code -Dparallel=none}) and with four threads in
 * CI ({@code -Dthreads=4}) without editing XML.
 */
public class ExecutionSettingsListener implements IAlterSuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSettingsListener.class);

    @Override
    public void alter(List<XmlSuite> suites) {
        ExecutionSettings settings = ConfigManager.config().runSettings();
        // Called once, before any suite starts: the XML is changed in memory, not on disk
        for (XmlSuite suite : suites) {
            // "none" / "classes" / "methods" / "tests" -> TestNG's enum
            suite.setParallel(XmlSuite.ParallelMode.getValidParallel(settings.parallel()));
            suite.setThreadCount(settings.threads());
            // Data-provider rows of parallel data providers use the same thread budget
            suite.setDataProviderThreadCount(settings.threads());
            LOG.info("Suite '{}': parallel={}, threads={}", suite.getName(), settings.parallel(),
                    settings.isParallel() ? settings.threads() : 1);
        }
    }
}
