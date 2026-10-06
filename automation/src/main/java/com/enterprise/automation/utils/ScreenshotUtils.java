package com.enterprise.automation.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final Path DIRECTORY = Path.of("target", "screenshots");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private ScreenshotUtils() {
    }

    /** PNG bytes of the current browser window, or {@code null} if no screenshot could be taken. */
    public static byte[] capture(WebDriver driver) {
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            LOG.warn("Could not take a screenshot: {}", e.getMessage());
            return null;
        }
    }

    /** Saves a screenshot to {@code target/screenshots/<name>-<timestamp>.png}; returns its path or {@code null}. */
    public static Path save(WebDriver driver, String name) {
        byte[] png = capture(driver);
        if (png == null) {
            return null;
        }
        try {
            Files.createDirectories(DIRECTORY);
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
