package com.enterprise.automation.data;

import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Undo actions for the data a test creates, per thread.
 *
 * <p>A test registers the clean-up right after it creates something ("delete customer X"), and
 * the base test runs all registered actions after the test, whether it passed or failed. Actions
 * run newest first (an order is removed before its customer). A failing clean-up is logged, not
 * thrown, so it never hides the test's own result; the remaining actions still run.
 */
public final class CleanupRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CleanupRegistry.class);
    // A stack per thread: parallel tests never run each other's clean-ups
    private static final ThreadLocal<Deque<Action>> ACTIONS = ThreadLocal.withInitial(ArrayDeque::new);

    // Static access only
    private CleanupRegistry() {
    }

    /** Something that undoes test data; may throw. */
    @FunctionalInterface
    public interface Cleanup {
        // "throws Exception" lets clean-ups call JDBC/IO code without wrapping
        void run() throws Exception;
    }

    // A clean-up and a description for the log
    private record Action(String description, Cleanup cleanup) {
    }

    /** Registers a clean-up for this thread's current test. */
    public static void register(String description, Cleanup cleanup) {
        // push = add on top: the newest clean-up runs first
        ACTIONS.get().push(new Action(description, cleanup));
    }

    /** Runs and clears every action of this thread, newest first. Returns how many failed. */
    public static int runAll() {
        Deque<Action> actions = ACTIONS.get();
        int failures = 0;
        while (!actions.isEmpty()) {
            // Newest first: e.g. an order is removed before the customer it belongs to
            Action action = actions.pop();
            try {
                action.cleanup().run();
                LOG.debug("Cleaned up: {}", action.description());
            } catch (Exception | AssertionError e) {
                // AssertionError too: clean-ups reuse framework helpers that assert (expectStatus)
                failures++;
                LOG.warn("Clean-up failed ({}): {}", action.description(), e.toString());
            }
        }
        // Free the slot for the next test on this (pooled) thread
        ACTIONS.remove();
        return failures;
    }

    /** Number of clean-ups waiting on this thread. */
    public static int pending() {
        return ACTIONS.get().size();
    }
}
