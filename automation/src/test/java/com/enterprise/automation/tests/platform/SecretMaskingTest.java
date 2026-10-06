package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.SecretMasker;
import com.enterprise.automation.api.models.LoginRequest;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.config.DatabaseConfig;
import org.testng.annotations.Test;

/** Secrets never reach logs or reports: every path that prints data masks them. */
@Test(groups = {"unit", "security"})
public class SecretMaskingTest {

    public void jsonSecretFieldsAreMasked() {
        String json = "{\"username\":\"admin\",\"password\":\"S3cret!\",\"token\":\"abc123\",\"apiKey\":\"k\"}";

        String masked = SecretMasker.mask(json);

        assertThat(masked).contains("\"username\":\"admin\"")
                .contains("\"password\":\"****\"")
                .contains("\"token\":\"****\"")
                .contains("\"apiKey\":\"****\"")
                .doesNotContain("S3cret!").doesNotContain("abc123");
    }

    public void bearerTokensAreMasked() {
        assertThat(SecretMasker.mask("Authorization: Bearer eyJhbGciOi.payload.sig")).isEqualTo("Authorization: Bearer ****");
    }

    public void ordinaryTextIsUnchanged() {
        String text = "{\"email\":\"ava.patel@example.com\",\"city\":\"Austin\"}";

        assertThat(SecretMasker.mask(text)).isEqualTo(text);
    }

    public void objectsWithSecretsMaskThemInToString() {
        assertThat(new Credentials("admin", "S3cret!").toString()).doesNotContain("S3cret!");
        assertThat(new LoginRequest("admin", "S3cret!").toString()).doesNotContain("S3cret!");
        assertThat(new LoginResponse("tok3n", "Bearer", 3600, "admin", "Alex", "ADMIN").toString()).doesNotContain("tok3n");
        assertThat(new DatabaseConfig("jdbc:h2:mem:x", "sa", "S3cret!").toString()).doesNotContain("S3cret!");
    }
}
