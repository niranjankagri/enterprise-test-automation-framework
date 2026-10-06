package com.enterprise.demoapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** The one JSON mapper of the application (thread-safe once configured). */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            // Support java.time types (Instant timestamps in responses)
            .registerModule(new JavaTimeModule())
            // Write timestamps as ISO-8601 text ("2026-10-06T14:00:00Z"), not as numbers
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Constants holder: no instances
    private Json() {
    }
}
