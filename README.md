# Enterprise Test Automation Framework

A production-style QA automation framework: UI, API and database testing with Java 17, Selenium, TestNG and REST Assured.

> **Status:** Milestones 1–3 of 10 (foundation, configuration and driver platform, UI automation framework) are done. The roadmap is below; this README grows with each milestone.

Its companion repository, [selenium-java-framework](https://github.com/niranjankagri/selenium-java-framework), is an interview-focused Selenium + Java + TestNG lab. This repository holds the production-style work that lab leaves out.

## Application under test

[`demo-app/`](demo-app/README.md) holds **ShopEase Admin**, a small shop back office (login, dashboard, customers, products, checkout, orders) with a REST API (bearer tokens, ADMIN and read-only VIEWER roles) and an H2 database. It lives in this repository so the same data can be checked through the UI, the API and the database. For `-Denv=local` the suite starts it automatically; for `qa` it runs separately (`java -jar demo-app/target/demo-app.jar --port 8081`).

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
├── demo-app/            the application under test (UI + REST API + H2)
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

## UI automation

```text
Test (tests.ui)            business steps and assertions only
  └─ Page (ui.pages)       LoginPage, DashboardPage, CustomerPage, ProductPage, CheckoutPage, OrderPage
       └─ Component        HeaderComponent, NavigationComponent, TableComponent, ModalComponent, ToastComponent
            └─ ElementActions + WaitUtils     every action waits for the right condition first
                 └─ DriverManager             this thread's browser
```

- Locators are `data-testid` attributes (`TestId.of("customer-search")`), each defined once in its page or component.
- No `Thread.sleep` and no implicit wait. Pages wait for the application's "ready" signal (`body[data-ready]`, loading bar hidden); toasts and tables are read inside waits.
- Tables are read by column name: `table.row("Email", email).get("City")`.
- `BaseTest` gives every test a fresh browser, saves a screenshot to `automation/target/screenshots/` when a test fails, and always quits the browser.

```java
CheckoutPage checkout = loginAsAdmin().navigation().openProducts()
        .addToCart("Wireless Mouse")
        .header().openCart();
assertThat(checkout.total()).isEqualTo("$49.00");
```

## Running the tests

Requirements: JDK 17 or newer, Maven 3.9+, Chrome or Edge (Selenium Manager can download Firefox).

```bash
mvn clean test                                  # local: starts the demo app, Chrome
mvn clean test -Denv=qa                         # qa: app already running on 8081, headless
mvn clean test -Denv=qa -Dbrowser=edge          # qa on Edge
mvn clean test -Dbrowser=firefox -Dheadless=true
```

Logs go to the console (INFO) and to `automation/target/logs/automation.log` (DEBUG, with thread names).

## Roadmap

| # | Milestone | Status |
|---|---|---|
| 1 | Foundation & architecture | ✅ done |
| 2 | Configuration & driver platform | ✅ done |
| 3 | UI automation framework | ✅ done |
| 4 | Test data & UI coverage | planned |
| 5 | API automation platform | planned |
| 6 | Database & end-to-end integration | planned |
| 7 | Execution engine, parallelism & resilience | planned |
| 8 | Observability & reporting | planned |
| 9 | Docker, Selenium Grid, cloud & CI/CD | planned |
| 10 | Quality engineering, documentation & portfolio polish | planned |

## License

[MIT](LICENSE)
