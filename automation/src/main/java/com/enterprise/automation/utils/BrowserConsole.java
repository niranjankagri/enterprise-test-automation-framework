package com.enterprise.automation.utils;

import com.enterprise.automation.reporting.SecretMasker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

/**
 * Reads the browser console (JavaScript errors, failed requests, console.log) for failure evidence.
 *
 * <p>Only Chrome and Edge provide it (with the logging preference set in BrowserOptionsFactory);
 * for Firefox, or a crashed browser, the result is empty instead of an error.
 */
public final class BrowserConsole {

    // Enough to see what led to a failure, small enough to read in a report
    private static final int MAX_ENTRIES = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private BrowserConsole() {
    }

    /** The last console entries as "time LEVEL message" lines (secrets masked), or empty if none or unsupported. */
    public static Optional<String> read(WebDriver driver) {
        List<LogEntry> entries;
        try {
            // Reading drains the browser's buffer: called once, after the test method
            entries = driver.manage().logs().get(LogType.BROWSER).getAll();
        } catch (RuntimeException e) {
            // Firefox: UnsupportedCommandException; gone browser: session errors. Evidence is optional.
            return Optional.empty();
        }
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        // Keep the most recent entries: the ones closest to the failure
        List<LogEntry> last = entries.subList(Math.max(0, entries.size() - MAX_ENTRIES), entries.size());
        return Optional.of(last.stream()
                .map(e -> TIME.format(Instant.ofEpochMilli(e.getTimestamp())) + " " + e.getLevel() + " "
                        + SecretMasker.mask(e.getMessage()))
                .collect(Collectors.joining(System.lineSeparator())));
    }
}
