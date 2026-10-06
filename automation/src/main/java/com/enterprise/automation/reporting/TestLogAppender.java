package com.enterprise.automation.reporting;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Collects the log lines a test writes, so the report can show each test's own log.
 *
 * <p>Buffers are per thread: a test runs on one thread, so in a parallel run every test still gets
 * exactly its own lines. {@link #startCapture()} when a test starts, {@link #drainCapture()} when it ends.
 * Declared in {@code logback.xml}.
 */
public class TestLogAppender extends AppenderBase<ILoggingEvent> {

    // null = not capturing on this thread (e.g. between tests)
    private static final ThreadLocal<StringBuilder> BUFFER = new ThreadLocal<>();
    // Time of day is enough inside one test's log
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** Starts collecting this thread's log lines. */
    public static void startCapture() {
        BUFFER.set(new StringBuilder());
    }

    /** Returns the collected lines and stops collecting. */
    public static String drainCapture() {
        StringBuilder buffer = BUFFER.get();
        BUFFER.remove();
        return buffer == null ? "" : buffer.toString();
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Logback calls this on the thread that logged, so BUFFER is that thread's (that test's) buffer
        StringBuilder buffer = BUFFER.get();
        if (buffer == null) {
            return;
        }
        String logger = event.getLoggerName();
        // "14:02:11.512 INFO  ApiLoggingFilter - POST /api/customers -> 201 in 35 ms"
        buffer.append(TIME.format(Instant.ofEpochMilli(event.getTimeStamp()))).append(' ')
                .append(String.format("%-5s", event.getLevel())).append(' ')
                .append(logger.substring(logger.lastIndexOf('.') + 1)).append(" - ")
                .append(event.getFormattedMessage()).append('\n');
    }
}
