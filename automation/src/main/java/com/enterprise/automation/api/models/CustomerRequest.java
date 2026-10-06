package com.enterprise.automation.api.models;

import com.enterprise.automation.data.CustomerData;

/** Body of {@code POST/PUT /api/customers}. {@code null} fields are left out of the JSON. */
public record CustomerRequest(String firstName, String lastName, String email, String phone, String city,
                              String status) {

    /** Request body from a test-data record (status left out: the API defaults it to ACTIVE). */
    public static CustomerRequest from(CustomerData customer) {
        return new CustomerRequest(customer.firstName(), customer.lastName(), customer.email(), customer.phone(),
                customer.city(), null);
    }

    /** Same request with an explicit status, e.g. INACTIVE. */
    public CustomerRequest withStatus(String value) {
        return new CustomerRequest(firstName, lastName, email, phone, city, value);
    }
}
