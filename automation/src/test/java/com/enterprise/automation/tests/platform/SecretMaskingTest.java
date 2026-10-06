package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.models.LoginRequest;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.config.DatabaseConfig;
import com.enterprise.automation.reporting.SecretMasker;
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

    public void moreSecretFieldNamesAreMasked() {
        String json = "{\"clientSecret\":\"cs1\",\"refresh_token\":\"rt1\",\"newPassword\":\"np1\",\"cookie\":\"c1\"}";

        assertThat(SecretMasker.mask(json)).doesNotContain("cs1").doesNotContain("rt1").doesNotContain("np1")
                .doesNotContain("c1\"").contains("\"clientSecret\":\"****\"");
    }

    public void keyValueSecretsInTextAreMasked() {
        String text = "login with password=S3cret! and client_secret: cs1&api_key=k1";

        assertThat(SecretMasker.mask(text)).isEqualTo("login with password=**** and client_secret: ****&api_key=****");
    }

    public void credentialHeadersAreMaskedCompletely() {
        String headers = "Authorization: Basic YWRtaW46cHc=\nCookie: session=abc\nX-Api-Key: k1\nAccept: */*";

        assertThat(SecretMasker.mask(headers))
                .isEqualTo("Authorization: ****\nCookie: ****\nX-Api-Key: ****\nAccept: */*");
        assertThat(SecretMasker.maskHeader("Authorization", "Bearer abc")).isEqualTo("Bearer ****");
        assertThat(SecretMasker.maskHeader("Set-Cookie", "session=abc")).isEqualTo("****");
        assertThat(SecretMasker.maskHeader("X-Request-Id", "r1")).isEqualTo("r1");
    }

    public void credentialsInUrlsAreMasked() {
        assertThat(SecretMasker.mask("jdbc:postgresql://shop:pw1@db:5432/shop")).isEqualTo("jdbc:postgresql://shop:****@db:5432/shop");
        assertThat(SecretMasker.mask("jdbc:h2:tcp://localhost:9092/shop;USER=sa;PASSWORD=pw1"))
                .isEqualTo("jdbc:h2:tcp://localhost:9092/shop;USER=sa;PASSWORD=****");
    }

    public void ordinaryTextIsUnchanged() {
        String text = "{\"email\":\"ava.patel@example.com\",\"city\":\"Austin\"}";

        assertThat(SecretMasker.mask(text)).isEqualTo(text);
        // Words like "token" in prose are not secrets; base URLs keep their port
        assertThat(SecretMasker.mask("401: Missing bearer token")).isEqualTo("401: Missing bearer token");
        assertThat(SecretMasker.mask("http://localhost:8080/api")).isEqualTo("http://localhost:8080/api");
    }

    public void objectsWithSecretsMaskThemInToString() {
        assertThat(new Credentials("admin", "S3cret!").toString()).doesNotContain("S3cret!");
        assertThat(new LoginRequest("admin", "S3cret!").toString()).doesNotContain("S3cret!");
        assertThat(new LoginResponse("tok3n", "Bearer", 3600, "admin", "Alex", "ADMIN").toString()).doesNotContain("tok3n");
        assertThat(new DatabaseConfig("jdbc:h2:mem:x", "sa", "S3cret!").toString()).doesNotContain("S3cret!");
    }
}
