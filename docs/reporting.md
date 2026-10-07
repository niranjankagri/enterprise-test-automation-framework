# Reporting and observability

## Generate and open the report

```bash
mvn clean test                                                        # writes automation/target/allure-results
npx allure-commandline serve automation/target/allure-results         # build and open the report
npx allure-commandline generate automation/target/allure-results -o automation/target/allure-report --clean
```

Requires Node.js (for `npx`) and Java. CI publishes the generated report as the `allure-report` build artifact of every pipeline run ([ci-cd.md](ci-cd.md)).

## What a failed test answers

| Question | Where in the report |
|---|---|
| What failed? | test name, the failed step, the assertion message, and a failure category (see below) |
| Where? | layer (epic: API, UI, Database, Integration, End-to-end, Framework), feature (test class), tags (groups) |
| When? | start time and duration of the test and of every step |
| Which environment / browser? | "Environment" panel: environment, base URL, API URL, browser, headless, execution, parallelism, database |
| Which build? | "Environment": framework version, Git commit, build number, build URL; "Executor": local run or CI build with a link |
| Which test data? | parameters of data-driven tests; typed values in UI steps (passwords as `****`); request bodies |
| Which API request and response? | a step per call (`POST /api/customers`) with "Request" and "Response 201" attachments: method, URL, headers, body, status, duration and the correlation id (`X-Request-Id`, the same id the application logs) |
| Which database rows? | a step per query (`DB: customer by email`) with query name, SQL, parameters, database URL (credentials masked), duration and rows; a failed query names the query and the database |
| Which screenshot? | "Screenshot at failure", plus "Page URL", "Page title", "Page source" and "Browser console" (JavaScript errors, failed requests; Chrome/Edge, last 100 entries, masked); also saved in `automation/target/screenshots` |
| Which log? | "Test log": only this test's log lines, even in parallel runs |

## Failure categories

`automation/src/main/resources/allure/categories.json` sorts every failure into one category ("Categories" tab), so a lead sees the kind of problem before opening a test. The first matching rule wins, in this order:

| Category | Matches | Typical action |
|---|---|---|
| Configuration failure | message: unknown environment/browser/execution/parallel mode, missing configuration file or secret, invalid value | fix the run's configuration |
| Infrastructure failure | message: session not created, unreachable browser, connection refused/reset, renderer timeout | check browser, Grid, network; not an application result |
| Timeout | stack trace: Selenium `TimeoutException` (an explicit wait expired) | slow application or changed UI: read the wait's condition |
| Authentication / authorization failure | message: `Expected HTTP x but got 401/403` or `Expected HTTP 401/403 but got x` | sign-in, token or role problem |
| Database failure | stack trace: framework `db` package, `tests.db`, `SQLException` | stored data differs, or the query failed |
| UI failure | stack trace: framework `ui` package, `tests.ui`, Selenium | page/component behaviour |
| API failure | stack trace: framework `api` package, `tests.api`, REST Assured | status, schema or payload |
| Assertion failure | any other failed check | product behaviour |
| Test or framework error | any other unexpected exception ("broken") | bug in the test or framework |
| Skipped | skipped tests (no database access, retried attempts) | none |

Every rule was checked with deliberately failing probe tests (one per category).

## How it works

| Piece | Responsibility |
|---|---|
| `Report` | single entry point for steps and attachments (Allure lambda API) |
| `ElementActions`, `BasePage`, `ModalComponent` | every click, typed value, selection and navigation is a step |
| `ReportingApiFilter` | every API call is a step with request/response attachments |
| `QueryExecutor` | every SQL query is a step with its rows |
| `ReportEvidenceListener` + `BrowserConsole` | labels (epic/feature/tags), per-test log, screenshot/URL/title/page source/browser console right after a failed test method |
| `TestLogAppender` | Logback appender collecting each thread's lines for the running test |
| `ExecutionMetadataListener` + `AllureRunFiles` | `environment.properties`, `executor.json`, `categories.json` |
| `FailureDiagnosticsListener` | the same facts as a block in the log, for people reading logs instead of the report |

## Secrets

One utility, `reporting.SecretMasker`, masks every text that leaves a test:

| Where | How |
|---|---|
| console and file log | `%maskedMsg` in `logback.xml` (`MaskingConverter`): every line, whoever logged it |
| per-test log in the report | `TestLogAppender` |
| API evidence and failure messages | `ReportingApiFilter`, `ApiLoggingFilter`, `ApiAssertions` |
| database evidence | `QueryExecutor` (plus `password*`/`salt` columns) |
| execution metadata | `ExecutionMetadata` (URLs with credentials) |
| test parameters | `ParameterMasking` (same name rule, `SecretMasker.isSecretName`) |

What it masks: JSON fields whose name contains `password`, `token`, `secret`, `apiKey`, `authorization` or `cookie` (`clientSecret`, `refresh_token`, `newPassword`...), the same names as `key=value` / `key: value` in text, `Bearer` tokens (`Bearer ****`), the whole value of `Authorization: Basic`, `Cookie`, `Set-Cookie` and `X-Api-Key`, and passwords in URLs (`user:****@host`, `;PASSWORD=****`). Password fields typed in the UI are logged as `****`, and records holding secrets mask them in `toString()`. `SecretMaskingTest` covers the rules; a check over a whole run's results and logs finds no demo password.

## Why lambda steps, not `@Step`

`@Step` annotations need the AspectJ weaver as a `-javaagent`. On JDK 27 the weaver fails at start-up ("cannot determine any valid method to define auxiliary classes"), and the annotated steps then silently disappear from the report. Lambda steps (`Allure.step(name, () -> ...)`) need no agent and work on every JDK. Because the steps are placed in the framework layers, tests get them without any code.
