package com.enterprise.automation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The framework's one JSON mapper for API bodies.
 *
 * <p>Owning the mapper (instead of relying on REST Assured's object-mapper detection) keeps
 * serialization identical whatever library version is on the classpath: records, {@code Instant}
 * timestamps, {@code null} fields left out of requests, unknown response fields tolerated
 * (contracts are checked by JSON schemas, not by the models).
 */
public final class JsonMapper {

    // Configured once; ObjectMapper is thread-safe for reading and writing after configuration
    private static final ObjectMapper MAPPER = new ObjectMapper()
            // Instant fields (createdAt) in responses
            .registerModule(new JavaTimeModule())
            // Dates as ISO text, like the API sends them
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // New response fields do not break the models (schemas catch contract changes instead)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // null request fields are left out, e.g. a customer without phone
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    // Static helpers only
    private JsonMapper() {
    }

    /** Object (record, map) -> JSON text. */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + value, e);
        }
    }

    /** JSON text -> one model; the failure message includes the JSON that did not fit. */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response is not a valid " + type.getSimpleName() + ": " + json, e);
        }
    }

    /** JSON text -> generic type, e.g. {@code new TypeReference<List<CustomerResponse>>() { }}. */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response is not a valid " + type.getType() + ": " + json, e);
        }
    }
}
