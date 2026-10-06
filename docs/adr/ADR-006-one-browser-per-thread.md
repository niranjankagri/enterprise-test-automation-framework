# ADR-006: One browser per thread

Status: accepted

- **Problem:** parallel tests (Milestone 7) must never share a browser, and a failed test must never leave a browser running.
- **Decision:** `DriverFactory` only creates a configured browser (local or Grid, same options either way); `DriverManager` holds it in a `ThreadLocal<WebDriver>`, quits a leftover before starting a new one, and always clears the `ThreadLocal`, even when `quit()` fails.
- **Reason:** creation (which browser, where) and ownership (who uses it, when it ends) change for different reasons, so they live in different classes. A `ThreadLocal` gives each TestNG worker thread its own session without passing drivers around. `DriverManagerTest` checks it: four sessions on two threads, all unique.
- **Trade-offs:** code running on a different thread from the test (rare) cannot see the test's browser. No implicit wait is set: only explicit waits are used, so timeouts stay predictable.

[All decisions](README.md) · [Architecture](../architecture.md)
