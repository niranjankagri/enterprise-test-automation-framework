package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.config.DatabaseConfig;
import com.enterprise.automation.db.DatabaseConnection;
import com.enterprise.automation.db.QueryExecutor;
import com.enterprise.automation.reporting.TestLogAppender;
import org.testng.annotations.Test;

/** Bound values remain usable by SQL but cannot escape into retained database diagnostics. */
@Test(groups = {"unit", "security"})
public class DatabaseEvidenceTest {
    public void rawBoundSecretsDoNotReachLogsOrFailureDetails() {
        String secret = "synthetic-unlabelled-db-value-7F2A";
        QueryExecutor sql = new QueryExecutor(new DatabaseConnection(
                new DatabaseConfig("jdbc:h2:mem:evidence;DB_CLOSE_DELAY=-1", "sa", "")));
        TestLogAppender.startCapture();
        try {
            assertThat(sql.queryForValue("SELECT CAST(? AS VARCHAR) AS token", secret)).isEqualTo(secret);
            assertThatThrownBy(() -> sql.queryForValue("SELECT CAST(? AS INTEGER)", secret))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(error -> {
                        assertThat(error.getMessage()).doesNotContain(secret).contains("SQLState");
                        assertThat(error.getCause()).isNull();
                    });
            assertThat(TestLogAppender.drainCapture()).doesNotContain(secret);
        } finally {
            TestLogAppender.drainCapture();
            sql.update("SHUTDOWN");
        }
    }
}
