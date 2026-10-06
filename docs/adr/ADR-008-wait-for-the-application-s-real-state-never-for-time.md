# ADR-008: Wait for the application's real state, never for time

Status: accepted

- **Problem:** the UI loads data asynchronously (the demo app adds 100 ms latency per call), so a test that reads too early sees old or empty data.
- **Decision:** the application exposes testability hooks (`data-testid`, `body[data-page]`, `body[data-ready]`, a visible loading bar), and the framework waits for them through `WaitUtils`. No `Thread.sleep`, no implicit waits.
- **Reason:** waiting for a condition is as fast as the application allows and as long as it needs. Fixed sleeps are either too short (flaky) or too long (slow).
- **Trade-offs:** the application must cooperate. In a real project these hooks are agreed with the developers as part of the definition of done.

[All decisions](README.md) · [Architecture](../architecture.md)
