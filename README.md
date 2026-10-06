# Enterprise Test Automation Framework

A production-style QA automation framework: UI, API and database testing with Java 17, Selenium, TestNG and REST Assured.

> **Status:** Milestones 1–6 of 10 (foundation, configuration and driver platform, UI automation framework, test data and UI coverage, API automation, database and integration) are done. The roadmap is below; this README grows with each milestone.

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

## API automation

```text
Test → ApiSession (account) → Service (one per resource) → ApiClient → REST API
```

```java
CustomerResponse created = ApiSession.admin().customers().createCustomer(TestDataFactory.newCustomer());

Response response = ApiSession.viewer().customers().create(CustomerRequest.from(customer));
ErrorResponse error = ApiAssertions.error(ApiAssertions.expectStatus(response, 403));
```

- **ApiClient**: base URL from configuration, JSON, bearer token, logging. Immutable, with no REST Assured global state, so it's parallel-safe.
- **Services** (`AuthService`, `UserService`, `CustomerService`, `ProductService`, `OrderService`): raw methods return the `Response` for status/header/error checks; typed methods (`createCustomer`, `placeOrder`...) check the expected status and return a model.
- **Models**: request/response records; secrets masked in `toString()`.
- **Authentication**: `ApiSession.admin()`, `viewer()`, `as(credentials)`, `withToken(...)`, `anonymous()`; tokens cached per account for the run.
- **Contract checks**: JSON schemas in `src/test/resources/schemas` (strict: no unexpected fields) via `ApiAssertions.matchesSchema(response, "customer")`.
- **Logging**: one line per call (`POST /api/customers -> 201 in 35 ms`); bodies at DEBUG with passwords and tokens masked.

Coverage: GET/POST/PUT/PATCH/DELETE on every resource; status codes 200/201/204/400/401/403/404/405/409; `Location` and `X-Request-Id` headers; payloads and computed values (order totals, stock reservation and release, status flow); authentication (login, invalid/expired/logged-out tokens) and authorization (viewer read-only, admin-only users API); validation messages per field (the same CSV drives UI and API negatives); malformed JSON and ids.

## Database validation

```java
ShopDatabase db = ShopDatabase.fromConfig();
assertRow(db.customerByEmail(email), "customer " + email)
        .hasValue("city", "Pune")
        .hasValue("status", "ACTIVE");
assertThat(db.stockOf("ACC-3002")).isEqualTo(148);
```

- `DatabaseConnection` reads `db.url` / `db.username` / `db.password` (or `DB_URL`...) from the configuration. Environments without database access (`staging`) **skip** database tests with a reason instead of failing them.
- `QueryExecutor` runs only parameterized SQL (`?` placeholders, never string concatenation) and returns rows as maps.
- `ShopDatabase` holds the application's named queries (customer by email, order lines, stock...), like page objects hold locators.
- `DatabaseAssertions` gives readable checks whose failure messages show the whole row.

Integration coverage:

| Flow | Test |
|---|---|
| API → DB | created/updated/deleted customers stored exactly as sent; order rows, lines, totals and stock; cancellation and deletion give stock back; a rejected order leaves no trace (transaction rollback); passwords stored salted and hashed |
| API → DB → UI | customer created through the API, verified in the database, then found in the UI |
| UI → API → DB | customer edited in the UI, verified through the API and in the database; order placed in the UI, checked through the API, its lines and the stock change checked in the database |

## Test data management

| Need | Where it comes from |
|---|---|
| New, unique, valid data | `TestDataFactory.newCustomer()`, `newProduct()`, `newUser(role)`: Datafaker values plus a run-unique suffix |
| A specific variant | `newCustomer().withEmail("bad")`: change only the field the test is about |
| Reference data | `testdata/products.json` → `TestDataFactory.catalogue()` |
| Negative cases | `testdata/invalid-customers.csv`, `testdata/login-negative.csv` → TestNG DataProviders |
| Expected values | computed from the data, e.g. `OrderData.displayTotal()` |

Isolation: every test creates its own data (unique email/SKU, safe in parallel and across runs) and registers its clean-up in `CleanupRegistry`; `BaseTest` runs the clean-ups after each test, passed or failed, newest first.

Test groups so far: `smoke` (fast, read-only), `sanity` (key happy paths and role checks), `regression` (full coverage, data-driven negatives), `e2e` (business journeys), plus `ui` and `unit`/`platform`.

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
| 4 | Test data & UI coverage | ✅ done |
| 5 | API automation platform | ✅ done |
| 6 | Database & end-to-end integration | ✅ done |
| 7 | Execution engine, parallelism & resilience | planned |
| 8 | Observability & reporting | planned |
| 9 | Docker, Selenium Grid, cloud & CI/CD | planned |
| 10 | Quality engineering, documentation & portfolio polish | planned |

## License

[MIT](LICENSE)
