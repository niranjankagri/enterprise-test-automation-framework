package com.enterprise.automation.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Reads test data from JSON files on the classpath ({@code src/test/resources/testdata/...})
 * straight into data records, e.g. {@code readList("testdata/products.json", ProductData.class)}.
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataReader() {
    }

    public static <T> List<T> readList(String resource, Class<T> type) {
        CollectionType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
        try (InputStream in = open(resource)) {
            return MAPPER.readValue(in, listType);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read test data " + resource, e);
        }
    }

    public static <T> T read(String resource, Class<T> type) {
        try (InputStream in = open(resource)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read test data " + resource, e);
        }
    }

    private static InputStream open(String resource) {
        InputStream in = JsonDataReader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("Test data file not found on the classpath: " + resource);
        }
        return in;
    }
}
