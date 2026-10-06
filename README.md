# Enterprise Test Automation Framework

A production-style QA automation framework: UI, API and database testing with Java 17, Selenium, TestNG and REST Assured.

> **Status:** Milestone 1 of 10 (foundation) is done. The roadmap is below; this README grows with each milestone.

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

## Running the tests

Requirements: JDK 17 or newer, Maven 3.9+.

```bash
mvn clean test
```

Logs go to the console (INFO) and to `automation/target/logs/automation.log` (DEBUG, with thread names).

## Roadmap

| # | Milestone | Status |
|---|---|---|
| 1 | Foundation & architecture | ✅ done |
| 2 | Configuration & driver platform | planned |
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
