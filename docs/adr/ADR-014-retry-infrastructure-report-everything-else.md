# ADR-014: Retry infrastructure, report everything else

Status: accepted

- **Problem:** a blanket retry turns flaky tests and real defects green; no retry at all lets a crashed browser or a lost Grid node fail a whole nightly run.
- **Decision:** `RetryAnalyzer` on every test (via `RetryTransformer`), retrying only failures `TransientFailures` classifies as infrastructure, at most `retry.count` times. Assertions and wait timeouts are never retried.
- **Reason:** keeps the signal honest: a red test means the application or the test needs attention. Retries are logged and visible as skipped attempts.
- **Trade-offs:** the classification is a list of known exception types and messages; a new kind of infrastructure failure must be added to it (it is unit-tested in `ExecutionEngineTest`).

[All decisions](README.md) · [Architecture](../architecture.md)
