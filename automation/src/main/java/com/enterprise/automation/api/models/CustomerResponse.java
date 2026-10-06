package com.enterprise.automation.api.models;

import java.time.Instant;

/** A customer as the API returns it. */
public record CustomerResponse(long id, String firstName, String lastName, String email, String phone, String city,
                               String status, Instant createdAt) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
