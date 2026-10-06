package com.enterprise.automation.api.models;

import java.util.Map;

/** The API's single error shape. {@code fieldErrors} is present for validation errors only. */
public record ErrorResponse(int status, String error, String message, String path, String timestamp,
                            Map<String, String> fieldErrors) {
}
