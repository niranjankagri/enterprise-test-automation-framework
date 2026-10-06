# Architecture

This document records how the framework is put together and why. It starts small and grows with each milestone.

## Layers

```text
Tests (src/test/java)            describe behaviour only, no Selenium or HTTP code
   │
   ▼
Page Objects / API services      business-level actions: "create customer", "checkout"
   │
   ▼
Components / ApiClient / DB      reusable technical building blocks
   │
   ▼
Driver / Config                  browsers, environments, execution mode
```

Dependencies only point downwards. A page never knows about a test, and `config` knows about nothing above it. That keeps every layer replaceable and testable on its own.

## Decisions

Each major decision is written as Problem → Options → Decision → Reason → Trade-offs.

### ADR-001: Multi-module Maven build

- **Problem:** the framework needs one place for versions, and later a second module (the application under test, see ADR-002).
- **Options:** (a) one single-module project; (b) a parent POM with modules.
- **Decision:** a parent POM (`pom.xml`) with the `automation` module.
- **Reason:** all versions live in the parent's `dependencyManagement`, modules declare only what they use, and new modules can be added without restructuring.
- **Trade-offs:** one extra directory level (`automation/src/...` instead of `src/...`).

### ADR-002: A self-hosted application under test

- **Problem:** the plan validates the same business data through the UI, the API and the database (for example, create a customer through the API, find it in the database, then see it in the UI). Public demo sites expose a UI and sometimes an API, but never their database, and their shared data changes under the tests.
- **Options:** (a) public demo sites, skipping database validation; (b) a small demo application kept in this repository.
- **Decision:** (b). A small shop administration app (login, customers, products, orders, checkout) with a REST API, bearer-token authentication and an H2 database, added as its own module in Milestone 3.
- **Reason:** it is the only way to show real UI → API → DB validation, and it makes runs deterministic: no shared data, no outages of someone else's site.
- **Trade-offs:** extra code to maintain that is not test code. It is kept deliberately small and lives in its own module, so the framework never depends on it.

### ADR-003: Framework code in `src/main`, tests in `src/test`

- **Problem:** where do page objects, clients and utilities belong?
- **Decision:** in `src/main/java` of the `automation` module; only test classes go in `src/test/java`.
- **Reason:** framework code is compiled, reviewed and reused like production code, and tests stay short and readable.
- **Trade-offs:** test libraries (TestNG, AssertJ) are compile-scope dependencies of the module.

### ADR-004: SLF4J + Logback for logging

- **Decision:** code logs only through the SLF4J API; Logback is the implementation.
- **Reason:** SLF4J is the standard facade, so the implementation can change without touching code. Logback writes INFO to the console and DEBUG (with thread names, needed once tests run in parallel) to `target/logs/automation.log`.

### ADR-005: Layered, immutable configuration

- **Problem:** the same tests must run against several environments (local, qa, staging), browsers and execution modes, from a laptop, CI and Docker, without editing code.
- **Options:** (a) one properties file edited per run; (b) system properties only; (c) layered sources: system property → environment variable → environment file → defaults.
- **Decision:** (c), implemented by `ConfigLoader`, exposed through `ConfigManager.config()` as an immutable `TestConfig` record.
- **Reason:** developers use `-D` options, CI and Docker use environment variables (and secrets), and the files hold the reviewed defaults. The loader takes its sources as maps, so every precedence rule is unit-tested (`ConfigLoaderTest`) without touching global state. A record cannot change mid-run, so parallel threads can share it.
- **Trade-offs:** the configuration is fixed once per JVM; switching environment means a new run (which is what CI does anyway). Invalid values fail at start-up instead of halfway through a run, which is intended.

### ADR-006: One browser per thread

- **Problem:** parallel tests (Milestone 7) must never share a browser, and a failed test must never leave a browser running.
- **Decision:** `DriverFactory` only creates a configured browser (local or Grid, same options either way); `DriverManager` holds it in a `ThreadLocal<WebDriver>`, quits a leftover before starting a new one, and always clears the `ThreadLocal`, even when `quit()` fails.
- **Reason:** creation (which browser, where) and ownership (who uses it, when it ends) change for different reasons, so they live in different classes. A `ThreadLocal` gives each TestNG worker thread its own session without passing drivers around. `DriverManagerTest` checks it: four sessions on two threads, all unique.
- **Trade-offs:** code running on a different thread from the test (rare) cannot see the test's browser. No implicit wait is set: only explicit waits are used, so timeouts stay predictable.

### ADR-007: Page Objects composed of Component Objects

- **Problem:** the header, menu, tables, dialogs and toasts appear on many screens. Coding them into every page object duplicates locators and logic.
- **Options:** (a) one large page object per screen; (b) page objects with inheritance for shared parts; (c) page objects composed of component objects.
- **Decision:** (c). `BasePage` and `BaseComponent` provide only driver, waits and actions. `ShopPage<T>` adds the application's shared layout and its ready contract. Components are scoped to a root element, so the same `TableComponent` serves customers, products, orders and the cart.
- **Reason:** each locator lives in exactly one class; a change in the table markup is one fix. Composition also lets a page hold two tables without confusion, which inheritance cannot express.
- **Trade-offs:** more, smaller classes. Navigation methods return the next page already loaded, which ties some pages together, but keeps tests readable.

### ADR-008: Wait for the application's real state, never for time

- **Problem:** the UI loads data asynchronously (the demo app adds 100 ms latency per call), so a test that reads too early sees old or empty data.
- **Decision:** the application exposes testability hooks (`data-testid`, `body[data-page]`, `body[data-ready]`, a visible loading bar), and the framework waits for them through `WaitUtils`. No `Thread.sleep`, no implicit waits.
- **Reason:** waiting for a condition is as fast as the application allows and as long as it needs. Fixed sleeps are either too short (flaky) or too long (slow).
- **Trade-offs:** the application must cooperate. In a real project these hooks are agreed with the developers as part of the definition of done.

### ADR-010: API services return raw responses and typed results

- **Problem:** API tests need two different things: negative tests must inspect status codes, headers and error bodies, while setup, clean-up and happy paths just want "create a customer and give me its id".
- **Options:** (a) services return only models (negative tests cannot see status codes); (b) services return only raw responses (every test repeats status checks and JSON mapping); (c) both, clearly named.
- **Decision:** (c). `create(...)`, `get(...)`... return the REST Assured `Response`; `createCustomer(...)`, `getCustomer(...)`... check the expected status (failure message includes the masked body) and return a record. `ApiClient` is immutable and builds every request from scratch; the framework owns its Jackson mapper.
- **Reason:** tests read as intent in both cases, and no test re-implements HTTP plumbing. Owning the mapper keeps (de)serialization stable regardless of which JSON library REST Assured detects.
- **Trade-offs:** two methods per operation. Response models ignore unknown fields, so contract drift is caught by the strict JSON schemas rather than by the models.

### ADR-011: Database checks through plain JDBC, optional per environment

- **Problem:** the database is the final truth for "was it really stored?", but production-like environments rarely allow test runners to connect to it.
- **Options:** (a) an ORM/JPA layer in the tests; (b) a query library; (c) plain JDBC with parameterized statements and named queries; and, separately, whether database access is mandatory.
- **Decision:** (c), with `db.url` optional: `DatabaseConnection.fromConfig()` throws TestNG's `SkipException` when it is missing, from a `@BeforeClass`, so the tests are reported as skipped with the reason.
- **Reason:** tests must see the raw stored values (an ORM would map them the same way the application does, hiding mapping bugs). JDBC needs no extra dependency; the driver is a runtime dependency only, so the layer stays database-neutral. A connection per query is simple and thread-safe at test volumes.
- **Trade-offs:** SQL is tied to the schema; keeping it in `ShopDatabase` limits a schema change to one class. No connection pool: fine for tests, not for load.

### ADR-012: Clean-ups are idempotent and never fail a test

- **Problem:** a test that deletes its own data, or fails halfway, leaves its registered clean-up with nothing (or something different) to remove.
- **Decision:** clean-ups tolerate "already gone" (`deleteCustomerIfExists`, find-then-delete by email), and `CleanupRegistry` logs any failure (exceptions and assertion errors) instead of throwing.
- **Reason:** the result of a test must reflect the behaviour under test, never the bookkeeping after it.
- **Trade-offs:** a clean-up that keeps failing is only visible in the log. Milestone 8 surfaces it in the report.

### ADR-013: Suites say what runs, configuration says how

- **Problem:** the same tests run as a quick PR gate, a nightly regression, serially for debugging and with many threads in CI. Encoding parallelism in XML means one XML file per combination.
- **Decision:** one suite file per purpose (`smoke`, `sanity`, `regression`, `api`, `ui`, `integration`, `e2e`, `full`) selecting groups; `ExecutionSettingsListener` (an `IAlterSuiteListener`) applies `parallel`/`threads` from configuration to whichever suite runs. Listeners are declared once per suite file.
- **Reason:** eight small files instead of a matrix; CI changes threads with a variable.
- **Trade-offs:** the listener list is repeated in each suite file (TestNG has no include mechanism for listeners in XML).

### ADR-014: Retry infrastructure, report everything else

- **Problem:** a blanket retry turns flaky tests and real defects green; no retry at all lets a crashed browser or a lost Grid node fail a whole nightly run.
- **Decision:** `RetryAnalyzer` on every test (via `RetryTransformer`), retrying only failures `TransientFailures` classifies as infrastructure, at most `retry.count` times. Assertions and wait timeouts are never retried.
- **Reason:** keeps the signal honest: a red test means the application or the test needs attention. Retries are logged and visible as skipped attempts.
- **Trade-offs:** the classification is a list of known exception types and messages; a new kind of infrastructure failure must be added to it (it is unit-tested in `ExecutionEngineTest`).

### ADR-009: Every test owns its data

- **Problem:** tests that share data (one "test customer" for everybody) break each other, cannot run in parallel and leave the environment dirtier after each run.
- **Options:** (a) shared fixture data; (b) reset the database before each run; (c) each test creates unique data and removes it afterwards.
- **Decision:** (c). `TestDataFactory` generates valid data with a run-unique suffix (`RandomDataGenerator`), and each test registers an undo action in `CleanupRegistry` right after creating something. Read-only reference data (the seeded catalogue) is described in `testdata/products.json`.
- **Reason:** works on any environment, including shared ones where a reset is not allowed; parallel-safe; a failing test still cleans up.
- **Trade-offs:** each test pays for its own setup. Since Milestone 5, setup and clean-up of UI tests go through the API (only the behaviour under test goes through the UI), which keeps that cost low; the end-to-end test creates its own buyer through the API and deletes the buyer's orders and the buyer afterwards. Products are only deactivated by the API (kept for order history), so test products remain as inactive rows.
