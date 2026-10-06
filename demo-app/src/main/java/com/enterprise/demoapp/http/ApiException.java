package com.enterprise.demoapp.http;

import java.util.Map;

/**
 * An error the API reports to the client with an HTTP status, e.g. 400 validation errors with
 * one message per field, 401, 403, 404 or 409.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final transient Map<String, String> fieldErrors;

    public ApiException(int status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(int status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public int status() {
        return status;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String what, long id) {
        return new ApiException(404, what + " " + id + " not found");
    }

    public static ApiException validation(Map<String, String> fieldErrors) {
        return new ApiException(400, "Validation failed", fieldErrors);
    }
}
