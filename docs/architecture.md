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
