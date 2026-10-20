package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.reporting.AllureRunFiles;
import com.enterprise.automation.reporting.Report;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.StepResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.testng.annotations.Test;

/** Sensitive URLs are redacted at the shared Allure output boundary for API and UI evidence. */
@Test(groups = {"unit", "security"})
public class ReportRedactionTest {

    /** Persisted API/UI text, JSON, HTML and exception evidence contain no synthetic URL secrets. */
    public void apiAndUiEvidenceRedactsUrlSecretsBeforeAllurePersistsThem() throws IOException {
        String marker = UUID.randomUUID().toString();
        String apiUrl = "https://api-user-" + marker + ":api-password-" + marker + "@api.example.test/items"
                + "?access_token=api-token-" + marker + "&page=2&probe=" + marker;
        String uiUrl = "https://ui-user-" + marker + ":ui-password-" + marker + "@shop.example.test/catalog"
                + "?session_token=ui-token-" + marker + "&page=3&probe=" + marker;

        Report.step("API GET " + apiUrl, () -> {
            Report.attachText("API request " + apiUrl, "GET " + apiUrl);
            Report.attachJson("API response " + apiUrl, "{\"next\":\"" + apiUrl + "\"}");
        });
        assertThat(Report.<String>step("API query " + apiUrl, () -> "query result"))
                .isEqualTo("query result");
        Report.step("UI open " + uiUrl, () -> {
            Report.attachText("Page URL " + marker, uiUrl);
            Report.attachHtml("Page source " + uiUrl, "<a href=\"" + uiUrl + "\">" + marker + "</a>");
            Report.attachPng("Screenshot " + uiUrl, new byte[] {0x01});
        });
        Report.log("UI checkpoint " + uiUrl);
        IllegalStateException navigationFailure = new IllegalStateException("Navigation failed at " + uiUrl);
        assertThatThrownBy(() -> Report.step("UI navigate " + uiUrl, () -> {
            throw navigationFailure;
        })).isSameAs(navigationFailure);
        Report.attachText("Browser evidence unavailable " + marker, navigationFailure.toString());

        List<String> persisted = reportArtifactsContaining(AllureRunFiles.resultsDirectory(), marker);
        String evidence = String.join("\n", persisted) + "\n" + currentReportMetadata();

        assertThat(persisted).isNotEmpty();
        assertThat(evidence)
                .contains("https://****@api.example.test/items?access_token=****&page=2&probe=" + marker)
                .contains("https://****@shop.example.test/catalog?session_token=****&page=3&probe=" + marker)
                .contains("API request https://****@api.example.test/items?access_token=****&page=2&probe=" + marker)
                .contains("Page source https://****@shop.example.test/catalog?session_token=****&page=3&probe=" + marker)
                .doesNotContain("api-user-" + marker, "api-password-" + marker, "api-token-" + marker,
                        "ui-user-" + marker, "ui-password-" + marker, "ui-token-" + marker);
    }

    private static List<String> reportArtifactsContaining(Path resultsDirectory, String marker) throws IOException {
        try (Stream<Path> files = Files.list(resultsDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(ReportRedactionTest::isTextAttachment)
                    .map(ReportRedactionTest::readIfContains)
                    .filter(content -> content != null && content.contains(marker))
                    .toList();
        }
    }

    private static String currentReportMetadata() {
        StringBuilder metadata = new StringBuilder();
        Allure.getLifecycle().updateTest(testCase -> {
            metadata.append(testCase.getName()).append('\n');
            appendAttachments(metadata, testCase.getAttachments());
            appendSteps(metadata, testCase.getSteps());
        });
        return metadata.toString();
    }

    private static void appendSteps(StringBuilder metadata, List<StepResult> steps) {
        for (StepResult step : steps) {
            metadata.append(step.getName()).append('\n');
            if (step.getStatusDetails() != null) {
                metadata.append(step.getStatusDetails().getMessage()).append('\n');
                metadata.append(step.getStatusDetails().getTrace()).append('\n');
            }
            appendAttachments(metadata, step.getAttachments());
            appendSteps(metadata, step.getSteps());
        }
    }

    private static void appendAttachments(StringBuilder metadata, List<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            metadata.append(attachment.getName()).append('\n');
        }
    }

    private static boolean isTextAttachment(Path file) {
        String name = file.getFileName().toString();
        return name.contains("-attachment")
                && (name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".html"));
    }

    private static String readIfContains(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect generated Allure attachment " + file.getFileName(), e);
        }
    }
}
