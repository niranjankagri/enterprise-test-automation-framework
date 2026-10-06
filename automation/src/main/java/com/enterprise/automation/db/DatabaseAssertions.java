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
        assertThat(row).as("%s exists in the database", description).isPresent();
        return new RowAssert(row.get(), description);
    }

    public static void assertNoRow(Optional<Map<String, Object>> row, String description) {
        assertThat(row).as("%s must not exist in the database", description).isEmpty();
    }

    /** Checks on one row. Numbers are compared by value ({@code 10.5} equals {@code 10.50}). */
    public static final class RowAssert {

        private final Map<String, Object> row;
        private final String description;

        private RowAssert(Map<String, Object> row, String description) {
            this.row = row;
            this.description = description;
        }

        public RowAssert hasValue(String column, Object expected) {
            assertThat(row).as("%s has a column '%s'", description, column).containsKey(column);
            Object actual = row.get(column);
            if (expected instanceof Number && actual instanceof Number) {
                assertThat(new BigDecimal(actual.toString()))
                        .as("%s.%s in %s", description, column, row)
                        .isEqualByComparingTo(new BigDecimal(expected.toString()));
            } else {
                assertThat(actual).as("%s.%s in %s", description, column, row).isEqualTo(expected);
            }
            return this;
        }

        public RowAssert hasNonNull(String column) {
            assertThat(row.get(column)).as("%s.%s in %s", description, column, row).isNotNull();
            return this;
        }

        public RowAssert hasNull(String column) {
            assertThat(row.get(column)).as("%s.%s in %s", description, column, row).isNull();
            return this;
        }

        public Map<String, Object> row() {
            return row;
        }
    }
}
