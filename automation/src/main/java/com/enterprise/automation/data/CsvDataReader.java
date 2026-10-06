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

    /** Every data row of the file as a column -> value map, in file order. */
    public static List<Map<String, String>> read(String resource) {
        // Class-loader lookup: the path is relative to the classpath root (no leading "/")
        InputStream in = CsvDataReader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("Test data file not found on the classpath: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            // Set by the first real line
            List<String> header = null;
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            // Line number for error messages (counts comments and blank lines too, like an editor)
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                // Blank lines and # comments are for the people maintaining the file
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                List<String> values = parseLine(line);
                if (header == null) {
                    header = values;
                    continue;
                }
                // A missing or extra comma is a data error: report the exact line
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

    /** Splits one CSV line into values; commas inside double quotes do not split. */
    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        // Characters of the value being read
        StringBuilder current = new StringBuilder();
        // Inside "..." commas are text, not separators
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                // "" inside quotes is an escaped quote character
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    // Closing quote
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                // Opening quote
                quoted = true;
            } else if (c == ',') {
                // End of a value
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        // The last value has no trailing comma
        values.add(current.toString().trim());
        return values;
    }
}
