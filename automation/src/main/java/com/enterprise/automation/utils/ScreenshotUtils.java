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
 * <p>Taking a screenshot must never hide the real failure. The page DOM is left intact while
 * temporary overlays cover marked and password content; evidence is omitted if masking, capture,
 * or restoration cannot be verified.
 */
public final class ScreenshotUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenshotUtils.class);
    // Relative to the module directory: automation/target/screenshots (cleaned by mvn clean)
    private static final Path DIRECTORY = Path.of("target", "screenshots");
    // Millisecond timestamp: parallel failures of the same test never overwrite each other
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final String INSTALL_MASKS = """
            const marker = "data-screenshot-mask-overlay";
            const targets = [...document.querySelectorAll("[data-sensitive], input[type='password']")];
            const overlays = [];
            try {
              for (const target of targets) {
                const rect = target.getBoundingClientRect();
                const left = Math.max(0, rect.left);
                const top = Math.max(0, rect.top);
                const right = Math.min(window.innerWidth, rect.right);
                const bottom = Math.min(window.innerHeight, rect.bottom);
                if (right <= left || bottom <= top) continue;

                const overlay = document.createElement("div");
                overlay.setAttribute(marker, "true");
                overlay.setAttribute("aria-hidden", "true");
                for (const [name, value] of Object.entries({
                  position: "fixed", left: `${rect.left}px`, top: `${rect.top}px`,
                  width: `${rect.width}px`, height: `${rect.height}px`,
                  "z-index": "2147483647", display: "flex", "align-items": "center",
                  "justify-content": "center", "box-sizing": "border-box", overflow: "hidden",
                  margin: "0", padding: "2px", border: "2px solid #b00020",
                  background: "#fff", color: "#000", opacity: "1", visibility: "visible",
                  "pointer-events": "none", transform: "none", "font-family": "sans-serif",
                  "font-size": "14px", "font-weight": "bold", "line-height": "normal",
                  "white-space": "nowrap"
                })) {
                  overlay.style.setProperty(name, value, "important");
                }
                overlay.textContent = "[REDACTED]";
                overlays.push(overlay);
                document.documentElement.appendChild(overlay);

                const x = (left + right) / 2;
                const y = (top + bottom) / 2;
                overlay.style.setProperty("pointer-events", "auto", "important");
                const hit = document.elementFromPoint(x, y);
                overlay.style.setProperty("pointer-events", "none", "important");
                if (!hit || !hit.hasAttribute(marker)) {
                  throw new Error("Screenshot masking overlay does not cover sensitive content");
                }
              }
              return overlays;
            } catch (error) {
              let cleanupFailed = false;
              for (const overlay of overlays) {
                try {
                  if (overlay.isConnected) overlay.parentNode.removeChild(overlay);
                } catch (cleanupError) {
                  cleanupFailed = true;
                }
              }
              if (cleanupFailed) {
                throw new Error("Could not clean up partial screenshot masks");
              }
              throw error;
            }
            """;

    private static final String REMOVE_MASKS = """
            let restored = true;
            for (const overlay of arguments[0]) {
              try {
                if (overlay.isConnected) overlay.parentNode.removeChild(overlay);
              } catch (error) {
                restored = false;
              }
              if (overlay.isConnected) restored = false;
            }
            return restored;
            """;

    private static final String REMOVE_ALL_MASKS = """
            let restored = true;
            for (const overlay of document.querySelectorAll("[data-screenshot-mask-overlay]")) {
              try {
                if (overlay.isConnected) overlay.parentNode.removeChild(overlay);
              } catch (error) {
                restored = false;
              }
              if (overlay.isConnected) restored = false;
            }
            return restored;
            """;

    // Static helpers only
    private ScreenshotUtils() {
    }

    /** PNG bytes with sensitive regions covered, or {@code null} if protection/capture fails. */
    public static byte[] capture(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor javascript) || !(driver instanceof TakesScreenshot screenshotDriver)) {
            LOG.warn("Could not protect sensitive page content for screenshot capture");
            return null;
        }

        List<?> overlays = null;
        byte[] screenshot = null;
        boolean restored = false;
        try {
            Object result = javascript.executeScript(INSTALL_MASKS);
            if (!(result instanceof List<?> installedOverlays)) {
                LOG.warn("Could not verify sensitive page masking for screenshot capture");
                try {
                    boolean cleaned = Boolean.TRUE.equals(javascript.executeScript(REMOVE_ALL_MASKS));
                    if (!cleaned) {
                        LOG.warn("Could not remove unverified sensitive page overlays");
                    }
                } catch (RuntimeException cleanupException) {
                    LOG.warn("Could not remove unverified sensitive page overlays: {}",
                            cleanupException.getClass().getSimpleName());
                }
                throw new IllegalStateException("Screenshot masking did not return its overlay handles");
            }
            overlays = installedOverlays;
            screenshot = screenshotDriver.getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            // Browser crashed or already quit: report it, do not hide the test's own failure
            LOG.warn("Could not take a screenshot: {}", e.getClass().getSimpleName());
        } finally {
            if (overlays != null) {
                try {
                    restored = Boolean.TRUE.equals(javascript.executeScript(REMOVE_MASKS, overlays));
                } catch (RuntimeException e) {
                    LOG.warn("Could not remove sensitive page overlays after screenshot capture: {}",
                            e.getClass().getSimpleName());
                }
            }
        }
        if (!restored) {
            LOG.warn("Screenshot masking/restoration was not verified; omitting screenshot");
            return null;
        }
        return screenshot;
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
