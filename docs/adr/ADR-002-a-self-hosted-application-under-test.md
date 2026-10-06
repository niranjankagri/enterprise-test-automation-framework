# ADR-002: A self-hosted application under test

Status: accepted

- **Problem:** the plan validates the same business data through the UI, the API and the database (for example, create a customer through the API, find it in the database, then see it in the UI). Public demo sites expose a UI and sometimes an API, but never their database, and their shared data changes under the tests.
- **Options:** (a) public demo sites, skipping database validation; (b) a small demo application kept in this repository.
- **Decision:** (b). A small shop administration app (login, customers, products, orders, checkout) with a REST API, bearer-token authentication and an H2 database, added as its own module in Milestone 3.
- **Reason:** it is the only way to show real UI → API → DB validation, and it makes runs deterministic: no shared data, no outages of someone else's site.
- **Trade-offs:** extra code to maintain that is not test code. It is kept deliberately small and lives in its own module, so the framework never depends on it.

[All decisions](README.md) · [Architecture](../architecture.md)
