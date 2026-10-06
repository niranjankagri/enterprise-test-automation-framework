# ADR-016: Selenium WebDriver and TestNG

Status: accepted

- **Problem:** the framework needs a browser automation library and a test runner that support cross-browser runs, remote browsers, parallel execution, grouping into suites and data-driven tests, and that the teams who will maintain it already know.
- **Options:** browser automation: (a) Selenium WebDriver, (b) Playwright, (c) Cypress. Test runner: (a) TestNG, (b) JUnit 5.
- **Decision:** Selenium WebDriver 4 with TestNG 7.
- **Reason:** Selenium is the W3C WebDriver standard, drives Chrome, Firefox and Edge through the same API, runs on Selenium Grid and every cloud provider, and Selenium Manager removes driver-binary handling. Cypress is JavaScript-only and runs inside the browser; Playwright is strong but brings its own browsers and runner model, and the portfolio's companion project already covers it. TestNG offers what this framework uses heavily, out of the box: suite XML files, groups, `parallel` modes with thread counts, data providers, `IRetryAnalyzer`, `IAlterSuiteListener` and invoked-method listeners (evidence before `@AfterMethod`).
- **Trade-offs:** Selenium needs explicit wait discipline (ADR-008) where Playwright auto-waits; the framework enforces it with `WaitUtils` and Checkstyle. TestNG's XML suites repeat the listener list per file (ADR-013). Surefire is held on 3.5.x because 3.6 dropped TestNG suite files.

[All decisions](README.md) · [Architecture](../architecture.md)
