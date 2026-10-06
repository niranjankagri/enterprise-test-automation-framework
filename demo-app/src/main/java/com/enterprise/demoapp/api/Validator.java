package com.enterprise.demoapp.api;

import com.enterprise.demoapp.http.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads fields from a JSON body and collects one message per invalid field, so a client gets
 * every problem in a single 400 response instead of one at a time.
 *
 * <p>{@code partial = true} (PATCH) skips "required" checks for absent fields.
 */
final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{7,20}$");

    private final JsonNode body;
    private final boolean partial;
    private final Map<String, String> errors = new LinkedHashMap<>();

    Validator(JsonNode body, boolean partial) {
        this.body = body;
        this.partial = partial;
    }

    boolean has(String field) {
        JsonNode node = body.get(field);
        return node != null && !node.isNull();
    }

    String text(String field, String label, int maxLength, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            if (required && !partial) {
                errors.put(field, label + " is required");
            }
            return null;
        }
        String value = node.asText().trim();
        if (value.length() > maxLength) {
            errors.put(field, label + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    String email(String field, boolean required) {
        String value = text(field, "Email", 120, required);
        if (value != null && !EMAIL.matcher(value).matches()) {
            errors.put(field, "Email must be a valid email address");
        }
        return value;
    }

    String phone(String field) {
        String value = text(field, "Phone", 20, false);
        if (value != null && !PHONE.matcher(value).matches()) {
            errors.put(field, "Phone must contain 7-20 digits, spaces, +, - or ()");
        }
        return value;
    }

    String oneOf(String field, String label, Set<String> allowed, boolean required) {
        String value = text(field, label, 20, required);
        if (value != null && !allowed.contains(value)) {
            errors.put(field, label + " must be one of " + allowed);
        }
        return value;
    }

    BigDecimal positiveDecimal(String field, String label, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required && !partial) {
                errors.put(field, label + " is required");
            }
            return null;
        }
        if (!node.isNumber() || node.decimalValue().signum() <= 0) {
            errors.put(field, label + " must be a positive number");
            return null;
        }
        return node.decimalValue();
    }

    Integer nonNegativeInt(String field, String label, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required && !partial) {
                errors.put(field, label + " is required");
            }
            return null;
        }
        if (!node.canConvertToInt() || !node.isIntegralNumber() || node.intValue() < 0) {
            errors.put(field, label + " must be a whole number of 0 or more");
            return null;
        }
        return node.intValue();
    }

    void error(String field, String message) {
        errors.put(field, message);
    }

    /** Throws one 400 with all collected field errors, if any. */
    void validate() {
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }
}
