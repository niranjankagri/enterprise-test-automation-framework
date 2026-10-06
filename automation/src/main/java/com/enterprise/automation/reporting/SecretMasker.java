package com.enterprise.automation.reporting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one masking utility of the framework: replaces secret values with {@code ****}.
 *
 * <p>Used everywhere text leaves the test: console and file logs (through {@link MaskingConverter}),
 * per-test logs, API evidence, database evidence, failure messages and the execution metadata.
 * Masks:
 * <ul>
 *   <li>JSON fields whose name contains password, token, secret, apiKey, authorization or cookie;</li>
 *   <li>{@code key=value} / {@code key: value} pairs with such a name (query strings, JDBC URLs, text);</li>
 *   <li>bearer tokens ({@code Bearer ****}), and the whole value of other credential headers
 *       ({@code Authorization: Basic}, {@code Cookie}, {@code Set-Cookie}, {@code X-Api-Key});</li>
 *   <li>passwords in URLs ({@code scheme://user:****@host}).</li>
 * </ul>
 */
public final class SecretMasker {

    /** What replaces a secret. */
    public static final String MASK = "****";

    // Word stems that make a name secret: "password", "newPassword", "access_token", "clientSecret"...
    private static final String SECRET_NAME = "[A-Za-z0-9_-]*(?:password|passwd|pwd|token|secret|api[_-]?key|authorization|cookie)"
            + "[A-Za-z0-9_-]*";
    // "password": "x" (any spacing): keeps the name and the quotes, replaces the value
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(\"" + SECRET_NAME + "\"\\s*:\\s*\")(?:[^\"\\\\]|\\\\.)*(\")", Pattern.CASE_INSENSITIVE);
    // Credential headers in text (one per line): the whole value, except bearer tokens (below)
    private static final Pattern HEADER = Pattern.compile(
            "^((?:Authorization|Proxy-Authorization|Cookie|Set-Cookie|X-Api-Key)\\s*:\\s*+)(?!Bearer\\s)(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    // (\s*+ is possessive: it may not give back the space, or "(?!Bearer" would be checked one character early)
    // "Bearer <token>" anywhere
    private static final Pattern BEARER = Pattern.compile("(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    // password=x, client_secret: x (not JSON: there a quote follows the name); value up to a separator
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?<![\"A-Za-z0-9_-])(" + SECRET_NAME + "\\s*[=:]\\s*+)(?![\\s\"]|Bearer\\s|\\*{4})([^\\s,;&\"'}\\]]+)",
            Pattern.CASE_INSENSITIVE);
    // scheme://user:password@host
    private static final Pattern URL_PASSWORD = Pattern.compile("(://[^/\\s:@]+:)[^@\\s/]+(@)");

    private SecretMasker() {
    }

    /** {@code text} with every secret replaced by {@code ****}; {@code null} stays {@code null}. */
    public static String mask(String text) {
        // Nothing to do for null/empty text (the common case for empty bodies)
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Order matters: header lines first (whole value), then the narrower patterns
        String masked = HEADER.matcher(text).replaceAll("$1" + MASK);
        masked = JSON_FIELD.matcher(masked).replaceAll("$1" + MASK + "$2");
        masked = BEARER.matcher(masked).replaceAll("$1" + MASK);
        masked = KEY_VALUE.matcher(masked).replaceAll("$1" + MASK);
        return URL_PASSWORD.matcher(masked).replaceAll("$1" + MASK + "$2");
    }

    /** Whether a name (parameter, header, field) suggests a secret: password, token, secret, apiKey... */
    public static boolean isSecretName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(SECRET_NAME.toLowerCase(Locale.ROOT));
    }

    /** A header value as it may be shown: bearer tokens as {@code Bearer ****}, other secrets fully masked. */
    public static String maskHeader(String name, String value) {
        if (!isSecretName(name)) {
            return value;
        }
        return value != null && value.regionMatches(true, 0, "Bearer ", 0, 7) ? "Bearer " + MASK : MASK;
    }
}
