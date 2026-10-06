package com.enterprise.automation.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Readable checks on database rows.
 *
 * <pre>{@code
 * DatabaseAssertions.assertRow(db.customerByEmail(email), "customer " + email)
 *         .hasValue("city", "Pune")
 *         .hasValue("status", "ACTIVE");
 * DatabaseAssertions.assertNoRow(db.customerByEmail(email), "deleted customer");
 * }</pre>
 *
 * Failure messages name the row and show its full content, so a failed check explains itself.
 */
public final class DatabaseAssertions {

    private DatabaseAssertions() {
    }

    /** Fails unless the row exists; returns a checker for its columns. */
    public static RowAssert assertRow(Optional<Map<String, Object>> row, String description) {
        // The description names the row in every failure message, e.g. "customer ava@... exists"
        assertThat(row).as("%s exists in the database", description).isPresent();
        return new RowAssert(row.get(), description);
    }

    public static void assertNoRow(Optional<Map<String, Object>> row, String description) {
        assertThat(row).as("%s must not exist in the database", description).isEmpty();
    }

    /** Checks on one row. Numbers are compared by value ({@code 10.5} equals {@code 10.50}). */
    public static final class RowAssert {

        // The row under test (column -> value) and its human-readable name
        private final Map<String, Object> row;
        private final String description;

        private RowAssert(Map<String, Object> row, String description) {
            this.row = row;
            this.description = description;
        }

        public RowAssert hasValue(String column, Object expected) {
            // A typo in the column name fails clearly instead of comparing against null
            assertThat(row).as("%s has a column '%s'", description, column).containsKey(column);
            Object actual = row.get(column);
            // Numbers by value: the database returns BigDecimal 10.50 / Long, tests write 10.5 / int
            if (expected instanceof Number && actual instanceof Number) {
                assertThat(new BigDecimal(actual.toString()))
                        .as("%s.%s in %s", description, column, row)
                        .isEqualByComparingTo(new BigDecimal(expected.toString()));
            } else {
                assertThat(actual).as("%s.%s in %s", description, column, row).isEqualTo(expected);
            }
            return this;
        }

        /** The column has some value (e.g. a generated timestamp). */
        public RowAssert hasNonNull(String column) {
            assertThat(row.get(column)).as("%s.%s in %s", description, column, row).isNotNull();
            return this;
        }

        /** The column is SQL NULL. */
        public RowAssert hasNull(String column) {
            assertThat(row.get(column)).as("%s.%s in %s", description, column, row).isNull();
            return this;
        }

        /** The raw row, for checks the helpers do not cover. */
        public Map<String, Object> row() {
            return row;
        }
    }
}
