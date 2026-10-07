# Demo and interview guide

A 5–10 minute walkthrough of the framework, and short answers to the questions it usually raises. Every answer points to the code or document that backs it.

## Before the demo

```bash
mvn -q clean test-compile                      # dependencies downloaded, code compiled
npx -y allure-commandline --version            # Allure CLI cached
```

Open in the IDE: `README.md`, `CustomerManagementTest`, `CustomerApiTest`, `UiApiDatabaseTest`, `CustomerPage`, `ReportEvidenceListener`, `docker/docker-compose.yml`. Have the latest nightly run open on GitHub (Actions → Nightly regression).

## The walkthrough

| # | Minute | Show | Say |
|---|---|---|---|
| 1 | 0–1 | README architecture diagram | Tests → pages / API services / DB queries → components, `ApiClient`, `QueryExecutor`, waits → driver and configuration. Dependencies point down only; tests contain no Selenium, HTTP or SQL. |
| 2 | 1–2 | `mvn clean test -Dsuite=smoke` (≈35 s) | The suite starts the application under test itself (`env=local`); 20 checks, 2 threads. |
| 3 | 2–3 | `CustomerManagementTest.adminCreatesACustomer` → `CustomerPage` → `TableComponent` | A UI test reads as business steps; locators exist once, in the page or component; waits are explicit. |
| 4 | 3–4 | `CustomerApiTest.postCreatesACustomer` → `CustomerService` → `ApiClient` | Raw `Response` for negative tests, typed `createCustomer` for set-up; schema check; clean-up registered right after creation. |
| 5 | 4–5 | `UiApiDatabaseTest.orderPlacedInTheUiIsConsistentInApiAndDatabase` | One order checked in three places: what the UI shows = what the API returns = what the database stores. |
| 6 | 5–6 | `mvn clean test -Dthreads=4` (running in the background) | One browser per thread (`ThreadLocal`), unique data per test, per-thread clean-up and logs: 169 tests in ~150 s. |
| 7 | 6–8 | `npx allure-commandline serve automation/target/allure-results`; a failed test (or the README screenshot) | Steps with durations, masked password, request/response with correlation id, DB rows, screenshot, URL, title, page source, browser console, the test's own log, the failure category. |
| 8 | 8–9 | `docker/docker-compose.yml`, the nightly run on GitHub | App, Grid hub, Chrome/Firefox/Edge nodes and the test runner in one command; PR → main → nightly pipelines with evidence kept on failure. |
| 9 | 9–10 | `docs/adr/README.md` | Every major choice is written down with options and trade-offs; pick one the interviewer cares about. |

## Questions and short answers

### Architecture

| Question | Answer | Where |
|---|---|---|
| Why Page Objects? | One place per screen for locators and actions; tests read as behaviour, a markup change is one fix. | [ADR-007](adr/ADR-007-page-objects-composed-of-component-objects.md) |
| Why Component Objects? | Tables, modals, toasts and navigation repeat across pages; composition reuses them and lets a page hold two tables, which inheritance cannot. | ADR-007 |
| Why `ThreadLocal`? | Each TestNG worker thread needs its own browser without passing drivers around; creation (`DriverFactory`) and ownership (`DriverManager`) are separate. | [ADR-006](adr/ADR-006-one-browser-per-thread.md) |
| Why TestNG? | Suite files, groups, parallel modes with thread counts, data providers, retry analyzer and listeners out of the box. | [ADR-016](adr/ADR-016-selenium-webdriver-and-testng.md) |
| Why REST Assured? | Readable requests, schema validation and filters for logging and masked report evidence; wrapped in an immutable `ApiClient`. | [ADR-017](adr/ADR-017-rest-assured-for-api-testing.md) |
| Why a service layer? | Negative tests need raw responses, set-up needs typed results; both without repeating HTTP plumbing. | [ADR-010](adr/ADR-010-api-services-return-raw-responses-and-typed-results.md) |
| Why plain JDBC? | Tests must see raw stored values; an ORM would map them like the application does and hide mapping bugs. | [ADR-011](adr/ADR-011-database-checks-through-plain-jdbc-optional-per-environment.md) |
| Why Allure? | Steps, typed attachments, environment, categories and history from plain result files, so CI can merge jobs. | [ADR-018](adr/ADR-018-allure-for-reporting.md) |

### Scalability

| Question | Answer |
|---|---|
| 5,000 tests? | Keep the pyramid (most checks at API level), split suites by purpose, shard them across CI matrix jobs, run UI on a Grid with enough slots, and merge the Allure results (the report job already merges several jobs). |
| Reduce execution time? | Move checks down to the API where the UI adds nothing; set up and clean up through the API (already done for UI tests); split the largest data-driven classes, which set the critical path with `parallel=classes` ([parallel-execution.md](parallel-execution.md)); run only smoke on PRs. |
| Flaky tests? | Prevent first: wait for real application state, own data, no shared assertions. Retry only infrastructure failures ([ADR-014](adr/ADR-014-retry-infrastructure-report-everything-else.md)); retries are visible in the log and the report, and a test that needs them often is investigated, not retried more. |
| Scale the Grid? | More sessions per node or more node containers (`--scale chrome=3`); requests beyond capacity queue in the hub ([grid.md](grid.md)). Beyond one host: a Grid on several machines, or a cloud provider as another `RemoteWebDriver` target ([ADR-021](adr/ADR-021-cloud-execution-deferred.md)). |
| Isolate test data? | Every test creates uniquely named data and registers its removal; reference data is read-only; assertions only look at the test's own rows ([ADR-009](adr/ADR-009-every-test-owns-its-data.md)). Two regression runs against the same long-lived application both pass. |

### CI/CD

| Question | Answer |
|---|---|
| PR vs main vs nightly? | PR: fast gate (compile with warnings as errors, Checkstyle, unit tests, smoke, dependency review). Main: API and UI suites plus smoke on a second browser. Nightly: everything, every browser, the containerised Grid ([ci-cd.md](ci-cd.md)). |
| Quality gates? | Compilation without warnings, Checkstyle (no sleeps, no implicit waits, no empty catch...), all tests green, no new high-severity vulnerable dependency. |
| Failure artifacts? | Allure results and report, screenshots, logs, execution metadata, Surefire reports, uploaded with `if: always()`. |
| Secrets? | Only from environment variables / CI secrets; masked in every log line, report attachment and failure message by one utility (`SecretMasker`); `.env` is ignored, `.env.example` documents the names. |

### Leadership

| Question | Answer |
|---|---|
| Migrate a legacy framework? | Measure first (flakiness, run time, what is covered twice). Build the new layers next to the old ones, move the most valuable and most flaky tests first, run both in CI until the new one covers the same risks, then switch off the old suite. Write the decisions down (ADRs) so the team owns them. |
| Measure automation ROI? | Time from commit to trustworthy feedback, defects found before release vs after, manual regression hours saved, flaky-failure rate and the time spent triaging failures (here: report categories and evidence shorten triage). |
| API or UI level? | Business rules, validation and permissions at API level (fast, precise); UI for what only the UI can show (forms, field errors, role-based controls, cart behaviour) and a few end-to-end journeys ([test-strategy.md](test-strategy.md)). |
| What not to automate? | One-off checks, rapidly changing prototypes, visual judgement, exploratory testing, and anything whose automation costs more to keep green than the risk it covers. |
