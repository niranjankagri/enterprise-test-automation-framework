# ADR-012: Clean-ups are idempotent and never fail a test

Status: accepted

- **Problem:** a test that deletes its own data, or fails halfway, leaves its registered clean-up with nothing (or something different) to remove.
- **Decision:** clean-ups tolerate "already gone" (`deleteCustomerIfExists`, find-then-delete by email), and `CleanupRegistry` logs any failure (exceptions and assertion errors) instead of throwing.
- **Reason:** the result of a test must reflect the behaviour under test, never the bookkeeping after it.
- **Trade-offs:** a failing clean-up does not turn the test red: it is a `Clean-up failed (...)` warning in the log, and the clean-up's API call appears as a broken step under "Tear down" in the report. Someone has to look; a clean-up that keeps failing leaves data behind.

[All decisions](README.md) · [Architecture](../architecture.md)
