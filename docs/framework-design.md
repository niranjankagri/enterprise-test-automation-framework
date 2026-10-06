# Framework design

How to use and extend the framework. For the overall structure see [architecture.md](architecture.md).

## Writing a UI test

```java
@Test(groups = {"ui", "regression"})
public class CustomerManagementTest extends BaseTest {          // fresh browser per test, clean-up, quit

    public void adminCreatesACustomer() {
        CustomerData customer = TestDataFactory.newCustomer();  // unique, valid data
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();

        customers.addCustomer(customer);                        // a business action, not clicks

        assertThat(customers.search(customer.email()).table().row("Email", customer.email()))
                .containsEntry("City", customer.city());
    }
}
```

Rules: no Selenium in tests (pages and components own it), one behaviour per test, assertions with AssertJ, data from `TestDataFactory`, every created entity registered in `CleanupRegistry`.

## Adding a page

1. Create `ui/pages/XxxPage extends ShopPage<XxxPage>` with `pageId()` (value of `body[data-page]`) and `path()`.
2. Declare locators as `private static final By` using `TestId.of("...")`. Each locator exists once.
3. Expose business methods (`search`, `addCustomer`) built from `actions` (waits included) and components (`TableComponent`, `ModalComponent`).
4. Navigation methods return the next page, already loaded (`new XxxPage().waitUntilLoaded()`).

## Adding a component

Extend `BaseComponent` with the component's root locator; search children inside the root (`child(...)`) so the same component works anywhere on any page.

## Adding an API resource

1. Records in `api/models`: `XxxRequest` (with `from(XxxData)`), `XxxResponse`. Mask secrets in `toString()`.
2. `api/services/XxxService extends BaseService`: raw methods returning `Response` (`create`, `get`, ...) and typed happy paths (`createXxx` checks the status and returns the record).
3. Expose it from `ApiSession` (`xxx()`).
4. Add `schemas/xxx.json` (strict: `additionalProperties: false`) and check it with `ApiAssertions.matchesSchema`.

## Adding test data

- Generated: a record in `data` plus a `TestDataFactory.newXxx()` that is valid and unique by default (`RandomDataGenerator.uniqueSuffix()`).
- Data-driven: a CSV or JSON file in `src/test/resources/testdata`, a `@DataProvider` in `TestDataProviders` whose first column is a readable case name.

## Adding a database check

Add a named query to `ShopDatabase` (parameterized SQL only) and assert with `DatabaseAssertions.assertRow(...).hasValue(...)`. Classes with database checks obtain `ShopDatabase.fromConfig()` in a `@BeforeClass(alwaysRun = true)`, so environments without database access skip them.

## Adding a configuration setting

1. A `ConfigKey` (property name; the environment variable name is derived: `my.setting` → `MY_SETTING`).
2. A field in `TestConfig` (or a nested record) and its resolution in `ConfigLoader` (validated, typed).
3. A default in `config/default.properties` and, if it differs, in the environment files.
4. A test in `ConfigLoaderTest`.

## Listeners and where they run

| Listener | Hook | Purpose |
|---|---|---|
| `ExecutionSettingsListener` | alter suite | parallel mode, threads from configuration |
| `RetryTransformer` / `RetryAnalyzer` | annotation transform / after failure | retry transient infrastructure failures only |
| `TestLogContextListener` | test start/end | test name in log lines, START/PASS/FAIL/SKIP |
| `FailureDiagnosticsListener` | test failure | diagnostic block in the log |
| `ReportEvidenceListener` | test start / after test method | labels, per-test log, screenshot, URL, page source |
| `ExecutionMetadataListener` | suite start/end | run metadata, Allure environment/executor/categories |
| `DemoAppLifecycle` (test code) | suite start | start the application under test for `env=local` |

All listeners are declared in each suite file in `src/test/resources/suites`.
