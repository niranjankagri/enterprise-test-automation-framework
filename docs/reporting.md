# Reporting and observability

## Generate and open the report

```bash
mvn clean test                                                        # writes automation/target/allure-results
npx allure-commandline serve automation/target/allure-results         # build and open the report
npx allure-commandline generate automation/target/allure-results -o automation/target/allure-report --clean
```

Requires Node.js (for `npx`) and Java. CI publishes the generated report as a build artifact (Milestone 9).

## What a failed test answers

| Question | Where in the report |
|---|---|
| What failed? | test name, the failed step, the assertion message; category "Product defects" (assertion), "Test defects" (unexpected exception), "Application too slow or UI changed" (wait timeout) or "Infrastructure problems" |
| Where? | layer (epic: API, UI, Database, Integration, End-to-end, Framework), feature (test class), tags (groups) |
| When? | start time and duration of the test and of every step |
| Which environment / browser? | "Environment" panel: environment, base URL, API URL, browser, headless, execution, parallelism, database |
| Which build? | "Environment": framework version, Git commit, build number, build URL; "Executor": local run or CI build with a link |
| Which test data? | parameters of data-driven tests; typed values in UI steps (passwords as `****`); request bodies |
| Which API request and response? | a step per call (`POST /api/customers`) with "Request" and "Response 201" attachments: method, URL, headers, body |
| Which database rows? | a step per query (`SQL: SELECT ...`) with parameters and rows |
| Which screenshot? | "Screenshot at failure", plus "Page URL" and "Page source"; also saved in `automation/target/screenshots` |
| Which log? | "Test log": only this test's log lines, even in parallel runs |

## How it works

| Piece | Responsibility |
|---|---|
| `Report` | single entry point for steps and attachments (Allure lambda API) |
| `ElementActions`, `BasePage`, `ModalComponent` | every click, typed value, selection and navigation is a step |
| `ReportingApiFilter` | every API call is a step with request/response attachments |
| `QueryExecutor` | every SQL query is a step with its rows |
| `ReportEvidenceListener` | labels (epic/feature/tags), per-test log, screenshot/URL/page source right after a failed test method |
| `TestLogAppender` | Logback appender collecting each thread's lines for the running test |
| `ExecutionMetadataListener` + `AllureRunFiles` | `environment.properties`, `executor.json`, `categories.json` |
| `FailureDiagnosticsListener` | the same facts as a block in the log, for people reading logs instead of the report |

## Secrets

Everything is masked before it is written: `password`, `token`, `apiKey`... fields in bodies, `Authorization` headers, password fields typed in the UI, `password*`/`salt` database columns, and every model or configuration record with a secret has a masking `toString()`. `SecretMaskingTest` covers it; a check over a whole run's results found no demo password in any attachment.

## Why lambda steps, not `@Step`

`@Step` annotations need the AspectJ weaver as a `-javaagent`. On JDK 27 the weaver fails at start-up ("cannot determine any valid method to define auxiliary classes"), and the annotated steps then silently disappear from the report. Lambda steps (`Allure.step(name, () -> ...)`) need no agent and work on every JDK. Because the steps are placed in the framework layers, tests get them without any code.
