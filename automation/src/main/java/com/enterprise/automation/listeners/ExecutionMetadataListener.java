package com.enterprise.automation.listeners;

import com.enterprise.automation.reporting.AllureRunFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.ITestContext;

/**
 * Records what ran, where and with which result: writes {@code target/execution-metadata.json}
 * at the end of each suite and logs a one-line summary.
 */
public class ExecutionMetadataListener implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionMetadataListener.class);
    // automation/target/execution-metadata.json (CI uploads it with the results)
    private static final Path OUTPUT = Path.of("target", "execution-metadata.json");

    // Suite start, for the duration
    private Instant start;

    @Override
    public void onStart(ISuite suite) {
        start = Instant.now();
        // The facts at the top of every log, before any test output
        LOG.info("Run metadata: {}", ExecutionMetadata.collect());
    }

    @Override
    public void onFinish(ISuite suite) {
        // Add up the results of every <test> in the suite
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (ISuiteResult result : suite.getResults().values()) {
            ITestContext context = result.getTestContext();
            passed += context.getPassedTests().size();
            failed += context.getFailedTests().size();
            skipped += context.getSkippedTests().size();
        }
        Duration duration = Duration.between(start == null ? Instant.now() : start, Instant.now());

        // Results first, then the run facts
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("suite", suite.getName());
        metadata.put("start", String.valueOf(start));
        metadata.put("durationSeconds", duration.toSeconds());
        metadata.put("passed", passed);
        metadata.put("failed", failed);
        metadata.put("skipped", skipped);
        metadata.putAll(ExecutionMetadata.collect());
        try {
            Files.createDirectories(OUTPUT.getParent());
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(OUTPUT.toFile(), metadata);
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", OUTPUT, e.getMessage());
        }
        // Allure's environment/executor/categories files; a failure here must not fail the run
        try {
            AllureRunFiles.write(ExecutionMetadata.collect());
        } catch (RuntimeException e) {
            LOG.warn("Could not write the Allure run files: {}", e.getMessage());
        }
        LOG.info("Suite '{}' finished in {} s: {} passed, {} failed, {} skipped", suite.getName(), duration.toSeconds(),
                passed, failed, skipped);
    }
}
