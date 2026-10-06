# ADR-015: Report evidence is collected by the framework, not by tests

Status: accepted

- **Problem:** a failure report must answer what, where, when, which environment, browser, data, request, response, screenshot and log. Asking every test to attach those is unreliable.
- **Options:** (a) `@Step` annotations woven by AspectJ plus Allure's REST Assured filter; (b) steps placed in the framework layers with Allure's lambda API, plus an own API filter.
- **Decision:** (b). `ElementActions`/`BasePage`/`ModalComponent` (UI), `ReportingApiFilter` (API) and `QueryExecutor` (SQL) emit steps; `ReportEvidenceListener` adds labels, per-test logs and failure evidence; `AllureRunFiles` writes environment, executor and categories.
- **Reason:** the AspectJ weaver fails on JDK 27, so annotated steps would silently vanish; lambda steps work everywhere. Allure's REST Assured filter attaches tokens and passwords unmasked; the own filter masks them. Failure evidence is taken in `IInvokedMethodListener.afterInvocation`, before `@AfterMethod` quits the browser.
- **Trade-offs:** steps are as fine-grained as user actions (click, type), which makes long tests verbose; page-level steps could be added on top where a test needs a higher-level story.

[All decisions](README.md) · [Architecture](../architecture.md)
