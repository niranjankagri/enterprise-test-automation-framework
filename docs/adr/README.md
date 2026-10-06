# Architecture decision records

One file per decision, each written as Problem → Options → Decision → Reason → Trade-offs.
A decision is never edited away: when it changes, a new ADR supersedes it and the old one says so.

| ADR | Decision |
|---|---|
| [ADR-001](ADR-001-multi-module-maven-build.md) | Multi-module Maven build |
| [ADR-002](ADR-002-a-self-hosted-application-under-test.md) | A self-hosted application under test |
| [ADR-003](ADR-003-framework-code-in-src-main-tests-in-src-test.md) | Framework code in `src/main`, tests in `src/test` |
| [ADR-004](ADR-004-slf4j-logback-for-logging.md) | SLF4J + Logback for logging |
| [ADR-005](ADR-005-layered-immutable-configuration.md) | Layered, immutable configuration |
| [ADR-006](ADR-006-one-browser-per-thread.md) | One browser per thread |
| [ADR-007](ADR-007-page-objects-composed-of-component-objects.md) | Page Objects composed of Component Objects |
| [ADR-008](ADR-008-wait-for-the-application-s-real-state-never-for-time.md) | Wait for the application's real state, never for time |
| [ADR-009](ADR-009-every-test-owns-its-data.md) | Every test owns its data |
| [ADR-010](ADR-010-api-services-return-raw-responses-and-typed-results.md) | API services return raw responses and typed results |
| [ADR-011](ADR-011-database-checks-through-plain-jdbc-optional-per-environment.md) | Database checks through plain JDBC, optional per environment |
| [ADR-012](ADR-012-clean-ups-are-idempotent-and-never-fail-a-test.md) | Clean-ups are idempotent and never fail a test |
| [ADR-013](ADR-013-suites-say-what-runs-configuration-says-how.md) | Suites say what runs, configuration says how |
| [ADR-014](ADR-014-retry-infrastructure-report-everything-else.md) | Retry infrastructure, report everything else |
| [ADR-015](ADR-015-report-evidence-is-collected-by-the-framework-not-by-tests.md) | Report evidence is collected by the framework, not by tests |
| [ADR-016](ADR-016-selenium-webdriver-and-testng.md) | Selenium WebDriver and TestNG |
| [ADR-017](ADR-017-rest-assured-for-api-testing.md) | REST Assured for API testing |
| [ADR-018](ADR-018-allure-for-reporting.md) | Allure for reporting |
| [ADR-019](ADR-019-docker-and-selenium-grid.md) | Docker and Selenium Grid for remote execution |
| [ADR-020](ADR-020-github-actions-for-ci-cd.md) | GitHub Actions for CI/CD |
| [ADR-021](ADR-021-cloud-execution-deferred.md) | Cloud browser execution is deferred, and will use one provider |

New decision: copy the format of an existing file, take the next number, add it to this table
and to the Decisions section of [architecture.md](../architecture.md).
