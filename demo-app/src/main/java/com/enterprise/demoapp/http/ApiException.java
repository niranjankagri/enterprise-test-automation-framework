package com.enterprise.demoapp.http;

import java.util.Map;

/**
 * An error the API reports to the client with an HTTP status, e.g. 400 validation errors with
 * one message per field, 401, 403, 404 or 409.
 *
 * <p>Thrown anywhere in a handler; the {@link Router} turns it into the standard error body.
 */
public class ApiException extends RuntimeException {

    // Exceptions are Serializable; a fixed id avoids a compiler warning
    private static final long serialVersionUID = 1L;

    // HTTP status sent to the client
    private final int status;
    // Field name -> message for validation errors (empty otherwise); transient: not serialized
    private final transient Map<String, String> fieldErrors;

    public ApiException(int status, String message) {
        // No field errors
        this(status, message, Map.of());
    }

    public ApiException(int status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        // Defensive, immutable copy
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public int status() {
        return status;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    /** 404 for a missing entity, e.g. "Customer 42 not found". */
    public static ApiException notFound(String what, long id) {
        return new ApiException(404, what + " " + id + " not found");
    }

    /** 400 with one message per invalid field. */
    public static ApiException validation(Map<String, String> fieldErrors) {
        return new ApiException(400, "Validation failed", fieldErrors);
    }
}
