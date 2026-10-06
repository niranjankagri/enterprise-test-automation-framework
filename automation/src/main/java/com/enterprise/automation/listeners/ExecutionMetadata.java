package com.enterprise.automation.listeners;

import com.enterprise.automation.FrameworkInfo;
import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.TestConfig;
import com.enterprise.automation.reporting.SecretMasker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Facts about a run that every report needs to answer "where and on what did this run?":
 * environment, URLs, browser, execution mode, parallelism, Java, OS, framework version, Git
 * commit and build number. No secrets: credentials are never part of it.
 */
public final class ExecutionMetadata {

    private ExecutionMetadata() {
    }

    /** Ordered key/value pairs, ready for a properties file, a JSON file or a report. */
    public static Map<String, String> collect() {
        TestConfig config = ConfigManager.config();
        // Ordered: the most useful facts first in every report and file
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("environment", config.environment());
        facts.put("base.url", config.baseUrl().toString());
        facts.put("api.base.url", config.apiBaseUrl().toString());
        facts.put("browser", config.browser().name().toLowerCase(Locale.ROOT));
        facts.put("headless", String.valueOf(config.headless()));
        facts.put("execution", config.execution().name().toLowerCase(Locale.ROOT));
        facts.put("parallel", config.runSettings().parallel());
        facts.put("threads", String.valueOf(config.runSettings().threads()));
        facts.put("retry.count", String.valueOf(config.runSettings().retryCount()));
        facts.put("database", config.database().map(db -> db.url()).orElse("not available"));
        facts.put("framework.version", FrameworkInfo.version());
        facts.put("java.version", System.getProperty("java.version"));
        facts.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        facts.put("git.commit", gitCommit());
        facts.put("build.number", firstNonBlank(System.getenv("GITHUB_RUN_NUMBER"), System.getenv("BUILD_NUMBER"), "local"));
        facts.put("build.url", buildUrl());
        // URLs may carry credentials (user:password@host, ;PASSWORD=...): mask before anything is written
        facts.replaceAll((key, value) -> SecretMasker.mask(value));
        return facts;
    }

    /** CI provides the commit (GITHUB_SHA, GIT_COMMIT); locally ask git, if it is installed. */
    static String gitCommit() {
        String fromCi = firstNonBlank(System.getenv("GITHUB_SHA"), System.getenv("GIT_COMMIT"), null);
        // Short form (12 characters) like the local git command below
        if (fromCi != null) {
            return fromCi.length() > 12 ? fromCi.substring(0, 12) : fromCi;
        }
        try {
            // Local run: ask git; at most 5 seconds, and "unknown" if git is missing
            Process git = new ProcessBuilder("git", "rev-parse", "--short=12", "HEAD").redirectErrorStream(true).start();
            if (git.waitFor(5, TimeUnit.SECONDS) && git.exitValue() == 0) {
                return new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            git.destroy();
        } catch (IOException e) {
            return "unknown";
        } catch (InterruptedException e) {
            // Keep the interrupt flag for whoever is waiting on this thread
            Thread.currentThread().interrupt();
        }
        return "unknown";
    }

    /** Link to the CI run (GitHub Actions variables, or Jenkins' BUILD_URL), "local" otherwise. */
    private static String buildUrl() {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repo = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");
        if (server != null && repo != null && runId != null) {
            return server + "/" + repo + "/actions/runs/" + runId;
        }
        return firstNonBlank(System.getenv("BUILD_URL"), "local", null);
    }

    /** The first of the three values that is neither null nor blank. */
    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return c;
    }
}
