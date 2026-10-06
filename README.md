# Enterprise Test Automation Framework

A production-style QA automation framework: UI, API and database testing with Java 17, Selenium, TestNG and REST Assured.

> **Status:** Milestones 1–2 of 10 (foundation, configuration and driver platform) are done. The roadmap is below; this README grows with each milestone.

Its companion repository, [selenium-java-framework](https://github.com/niranjankagri/selenium-java-framework), is an interview-focused Selenium + Java + TestNG lab. This repository holds the production-style work that lab leaves out.

## Technology stack

| Area | Tool |
|---|---|
| Language / build | Java 17, Maven (multi-module) |
| Test runner | TestNG |
| UI | Selenium 4 |
| API | REST Assured, Jackson |
| Assertions | AssertJ |
| Logging | SLF4J + Logback |
| Reporting | Allure |

## Project structure

```text
enterprise-test-automation-framework/
├── pom.xml              parent POM: every dependency and plugin version
├── automation/          the framework (src/main/java) and the tests (src/test/java)
├── docs/                architecture and design documentation
├── docker/              container setup (Milestone 9)
└── .github/             CI workflows (Milestone 9)
```

Framework packages (`automation/src/main/java/com/enterprise/automation`):

| Package | Responsibility |
|---|---|
| `config` | environment, browser and execution settings |
| `driver` | WebDriver lifecycle, one browser per thread |
| `ui.pages` / `ui.components` | Page Objects and reusable Component Objects |
| `api` | HTTP client, services, request/response models |
| `db` | JDBC access and database assertions |
| `data` | test data factories and readers |
| `listeners` | retry, failure diagnostics, execution metadata |
| `utils` | small stateless helpers |

See [docs/architecture.md](docs/architecture.md) for the design and the reasons behind it.

## Configuration

Every setting is resolved in this order (first match wins):

1. JVM system property: `-Dbrowser=edge`
2. Environment variable: `BROWSER=edge` (used by CI and Docker)
3. Environment file: `automation/src/main/resources/config/<env>.properties`
4. `config/default.properties`

| Environment | Selected with | Application URL | Notes |
|---|---|---|---|
| `local` (default) | nothing, or `-Denv=local` | `http://localhost:8080` | developer machine, visible browser |
| `qa` | `-Denv=qa` | `http://localhost:8081` | separately started application, headless |
| `staging` | `-Denv=staging` | placeholder host | real URLs and credentials come from CI secrets; remote browsers |

| Setting | Property / variable | Values (default) |
|---|---|---|
| Browser | `browser` / `BROWSER` | `chrome`, `firefox`, `edge` (`chrome`) |
| Headless | `headless` / `HEADLESS` | `true`, `false` (`false`) |
| Execution | `execution` / `EXECUTION` | `local`, `remote` (Grid), `cloud` (Milestone 9) (`local`) |
| Grid URL | `grid.url` / `GRID_URL` | (`http://localhost:4444`) |
| Window size | `window.width`, `window.height` | (`1440` × `900`) |
| Timeouts | `timeout.explicit.seconds`, `timeout.page.load.seconds` | (`10`, `30`) |

A wrong value fails the run immediately with a message that lists the valid values.

## Browsers

`DriverFactory` creates the browser and `DriverManager` holds it, one per thread (`ThreadLocal<WebDriver>`), so tests can run in parallel safely. Selenium Manager downloads the matching driver, and the browser itself if it is not installed. The framework uses explicit waits only (no implicit wait).

## Running the tests

Requirements: JDK 17 or newer, Maven 3.9+, Chrome or Edge (Selenium Manager can download Firefox).

```bash
mvn clean test                                  # local, Chrome
mvn clean test -Denv=qa                         # qa environment (headless)
mvn clean test -Denv=qa -Dbrowser=edge          # qa on Edge
mvn clean test -Dbrowser=firefox -Dheadless=true
```

Logs go to the console (INFO) and to `automation/target/logs/automation.log` (DEBUG, with thread names).

## Roadmap

| # | Milestone | Status |
|---|---|---|
| 1 | Foundation & architecture | ✅ done |
| 2 | Configuration & driver platform | ✅ done |
| 3 | UI automation framework | planned |
| 4 | Test data & UI coverage | planned |
| 5 | API automation platform | planned |
| 6 | Database & end-to-end integration | planned |
| 7 | Execution engine, parallelism & resilience | planned |
| 8 | Observability & reporting | planned |
| 9 | Docker, Selenium Grid, cloud & CI/CD | planned |
| 10 | Quality engineering, documentation & portfolio polish | planned |

## License

[MIT](LICENSE)
