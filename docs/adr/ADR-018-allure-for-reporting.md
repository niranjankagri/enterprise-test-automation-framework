# ADR-018: Allure for reporting

Status: accepted

- **Problem:** a failed test must be diagnosable from the report alone: steps, request/response, rows, screenshot, log, environment, build, failure kind, also for people who do not read Java.
- **Options:** (a) TestNG's built-in HTML reports; (b) ExtentReports; (c) Allure.
- **Decision:** (c), Allure (allure-testng 3) with steps and attachments created by the framework (ADR-015).
- **Reason:** Allure has nested steps with durations, typed attachments (images, HTML, text), labels (epic, feature, tags), an Environment and Executor panel, failure categories by message or stack-trace rules, history and retries; it is generated from plain result files, so CI can build one report from several jobs. TestNG's reports show only pass/fail and stack traces; ExtentReports needs the report to be built inside the test JVM.
- **Trade-offs:** the HTML report needs a generator (Allure CLI via `npx`, Node.js); results are many small files. Annotation-based steps need AspectJ, which does not run on JDK 27, so the framework uses Allure's lambda steps instead.

[All decisions](README.md) · [Architecture](../architecture.md)
