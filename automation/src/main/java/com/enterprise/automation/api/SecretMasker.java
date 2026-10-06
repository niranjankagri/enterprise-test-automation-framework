package com.enterprise.automation.api;

import java.util.regex.Pattern;

/** Replaces the values of secret JSON fields and bearer tokens with {@code ****}. */
public final class SecretMasker {

    private static final Pattern SECRET_FIELDS = Pattern.compile(
            "(\"(?:password|token|accessToken|refreshToken|apiKey|secret)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER = Pattern.compile("(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private SecretMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = SECRET_FIELDS.matcher(text).replaceAll("$1****$2");
        return BEARER.matcher(masked).replaceAll("$1****");
    }
}
