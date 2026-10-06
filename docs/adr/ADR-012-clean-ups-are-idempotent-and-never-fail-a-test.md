# ADR-012: Clean-ups are idempotent and never fail a test

Status: accepted

- **Problem:** a test that deletes its own data, or fails halfway, leaves its registered clean-up with nothing (or something different) to remove.
- **Decision:** clean-ups tolerate "already gone" (`deleteCustomerIfExists`, find-then-delete by email), and `CleanupRegistry` logs any failure (exceptions and assertion errors) instead of throwing.
- **Reason:** the result of a test must reflect the behaviour under test, never the bookkeeping after it.
- **Trade-offs:** a clean-up that keeps failing is only visible in the log. Milestone 8 surfaces it in the report.

[All decisions](README.md) · [Architecture](../architecture.md)
