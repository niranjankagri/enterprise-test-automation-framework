package com.enterprise.automation.data;

/**
 * A customer as tests create and expect it. Immutable; derive variants with the {@code with...}
 * methods, e.g. {@code customer.withEmail("not-an-email")} for a negative test.
 *
 * @param firstName first name
 * @param lastName  last name
 * @param email     unique email (the natural key of a customer)
 * @param phone     phone number, may be {@code null}
 * @param city      city, may be {@code null}
 */
public record CustomerData(String firstName, String lastName, String email, String phone, String city) {

    /** "First Last", as the UI shows it. */
    public String fullName() {
        return firstName + " " + lastName;
    }

    // with...(): a copy with one field changed (records are immutable), e.g. for negative tests
    public CustomerData withFirstName(String value) {
        return new CustomerData(value, lastName, email, phone, city);
    }

    /** A copy with another last name. */
    public CustomerData withLastName(String value) {
        return new CustomerData(firstName, value, email, phone, city);
    }

    /** A copy with another email address. */
    public CustomerData withEmail(String value) {
        return new CustomerData(firstName, lastName, value, phone, city);
    }

    /** A copy with another phone number (null for none). */
    public CustomerData withPhone(String value) {
        return new CustomerData(firstName, lastName, email, value, city);
    }

    /** A copy with another city. */
    public CustomerData withCity(String value) {
        return new CustomerData(firstName, lastName, email, phone, value);
    }
}
