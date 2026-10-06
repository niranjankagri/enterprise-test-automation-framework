/**
 * Request and response bodies as immutable records. Requests leave {@code null} fields out of the
 * JSON; responses ignore unknown fields (the contract is checked by JSON schemas instead).
 * Models carrying secrets mask them in {@code toString()}.
 */
package com.enterprise.automation.api.models;
