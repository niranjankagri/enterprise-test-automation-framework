package com.enterprise.automation.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves browser screenshots, e.g. at the moment a test fails.
 *
 * <p>Taking a screenshot must never hide the real failure: if the browser is already gone, the
 * problem is logged and {@code null} is returned instead of throwing.
 */
public final class ScreenshotUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenshotUtils.class);
    // Relative to the module directory: automation/target/screenshots (cleaned by mvn clean)
    private static final Path DIRECTORY = Path.of("target", "screenshots");
    // Millisecond timestamp: parallel failures of the same test never overwrite each other
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    // Static helpers only
    private ScreenshotUtils() {
    }

    /** PNG bytes of the current browser window, or {@code null} if no screenshot could be taken. */
    public static byte[] capture(WebDriver driver) {
        Object masked = null;
        try {
            if (driver instanceof JavascriptExecutor javascript) {
                masked = javascript.executeScript(
                        "return [...document.querySelectorAll('[data-sensitive],input[type=password]')].map(element => {"
                                + "const property = 'value' in element ? 'value' : 'textContent';"
                                + "const original = element[property]; element[property] = '[REDACTED]';"
                                + "return {element, property, original}; });");
            }
            // Every local and remote driver implements TakesScreenshot
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            // Browser crashed or already quit: report it, do not hide the test's own failure
            LOG.warn("Could not take a screenshot: {}", e.getClass().getSimpleName());
            return null;
        } finally {
            if (masked instanceof List<?> entries && driver instanceof JavascriptExecutor javascript) {
                try {
                    javascript.executeScript(
                            "arguments[0].forEach(entry => { entry.element[entry.property] = entry.original; });",
                            entries);
                } catch (RuntimeException e) {
                    LOG.warn("Could not restore sensitive page values after screenshot capture: {}",
                            e.getClass().getSimpleName());
                }
            }
        }
    }

    /** Saves a screenshot to {@code target/screenshots/<name>-<timestamp>.png}; returns its path or {@code null}. */
    public static Path save(WebDriver driver, String name) {
        byte[] png = capture(driver);
        // Nothing captured: nothing to save
        if (png == null) {
            return null;
        }
        try {
            Files.createDirectories(DIRECTORY);
            // Characters that are illegal in file names on some systems become "_"
            Path file = DIRECTORY.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_") + "-"
                    + LocalDateTime.now().format(STAMP) + ".png");
            Files.write(file, png);
            LOG.info("Screenshot saved: {}", file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            LOG.warn("Could not save the screenshot: {}", e.getMessage());
            return null;
        }
    }
}
