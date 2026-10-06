package com.enterprise.automation.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads CSV test data from the classpath: the first line holds the column names, every other
 * line becomes a {@code Map<column, value>}.
 *
 * <p>Supports double-quoted values (with commas and {@code ""} escapes), skips blank lines and
 * lines starting with {@code #}. Small on purpose: test data tables are tens of rows, maintained by
 * people in a spreadsheet or editor, so no CSV library is needed.
 */
public final class CsvDataReader {

    private CsvDataReader() {
    }

    public static List<Map<String, String>> read(String resource) {
        InputStream in = CsvDataReader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("Test data file not found on the classpath: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> header = null;
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> values = parseLine(line);
                if (header == null) {
                    header = values;
                    continue;
                }
                if (values.size() != header.size()) {
                    throw new IllegalStateException(resource + " line " + number + ": expected " + header.size()
                            + " values but found " + values.size());
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), values.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read test data " + resource, e);
        }
    }

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values;
    }
}
