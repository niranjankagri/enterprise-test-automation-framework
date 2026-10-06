# ADR-009: Every test owns its data

Status: accepted

- **Problem:** tests that share data (one "test customer" for everybody) break each other, cannot run in parallel and leave the environment dirtier after each run.
- **Options:** (a) shared fixture data; (b) reset the database before each run; (c) each test creates unique data and removes it afterwards.
- **Decision:** (c). `TestDataFactory` generates valid data with a run-unique suffix (`RandomDataGenerator`), and each test registers an undo action in `CleanupRegistry` right after creating something. Read-only reference data (the seeded catalogue) is described in `testdata/products.json`.
- **Reason:** works on any environment, including shared ones where a reset is not allowed; parallel-safe; a failing test still cleans up.
- **Trade-offs:** each test pays for its own setup. Since Milestone 5, setup and clean-up of UI tests go through the API (only the behaviour under test goes through the UI), which keeps that cost low; the end-to-end test creates its own buyer through the API and deletes the buyer's orders and the buyer afterwards. Products are only deactivated by the API (kept for order history), so test products remain as inactive rows.

[All decisions](README.md) · [Architecture](../architecture.md)
