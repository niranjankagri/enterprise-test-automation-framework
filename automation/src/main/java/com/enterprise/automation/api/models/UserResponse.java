package com.enterprise.automation.api.models;

import java.time.Instant;

/** A back-office account as the API returns it (never with a password). */
public record UserResponse(long id, String username, String fullName, String role, Instant createdAt) {
}
