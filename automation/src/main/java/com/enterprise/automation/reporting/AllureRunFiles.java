package com.enterprise.automation.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the files that describe a whole run to Allure:
 * <ul>
 *   <li>{@code environment.properties}: the "Environment" panel (environment, URLs, browser, Git
 *       commit, build number...);</li>
 *   <li>{@code executor.json}: who ran it (local machine or the CI build, with a link);</li>
 *   <li>{@code categories.json}: failure categories (product defect, test defect, infrastructure).</li>
 * </ul>
 */
public final class AllureRunFiles {

    private AllureRunFiles() {
    }

    /** The results directory configured in {@code allure.properties}. */
    public static Path resultsDirectory() {
        return Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
    }

    public static void write(Map<String, String> metadata) {
        Path dir = resultsDirectory();
        try {
            Files.createDirectories(dir);
            String environment = metadata.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue().replace("\\", "\\\\"))
                    .collect(Collectors.joining("\n"));
            Files.writeString(dir.resolve("environment.properties"), environment);

            Map<String, Object> executor = new LinkedHashMap<>();
            boolean ci = !"local".equals(metadata.get("build.number"));
            executor.put("name", ci ? "GitHub Actions" : "Local run");
            executor.put("type", ci ? "github" : "local");
            executor.put("buildName", ci ? "Build #" + metadata.get("build.number") : "Local run (" + metadata.get("git.commit") + ")");
            executor.put("buildOrder", ci ? metadata.get("build.number") : "0");
            if (ci) {
                executor.put("buildUrl", metadata.get("build.url"));
            }
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(dir.resolve("executor.json").toFile(), executor);

            try (InputStream categories = AllureRunFiles.class.getResourceAsStream("/allure/categories.json")) {
                if (categories != null) {
                    Files.copy(categories, dir.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write Allure run files to " + dir, e);
        }
    }
}
