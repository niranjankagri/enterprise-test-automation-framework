# ADR-011: Database checks through plain JDBC, optional per environment

Status: accepted

- **Problem:** the database is the final truth for "was it really stored?", but production-like environments rarely allow test runners to connect to it.
- **Options:** (a) an ORM/JPA layer in the tests; (b) a query library; (c) plain JDBC with parameterized statements and named queries; and, separately, whether database access is mandatory.
- **Decision:** (c), with `db.url` optional: `DatabaseConnection.fromConfig()` throws TestNG's `SkipException` when it is missing, from a `@BeforeClass`, so the tests are reported as skipped with the reason.
- **Reason:** tests must see the raw stored values (an ORM would map them the same way the application does, hiding mapping bugs). JDBC needs no extra dependency; the driver is a runtime dependency only, so the layer stays database-neutral. A connection per query is simple and thread-safe at test volumes.
- **Trade-offs:** SQL is tied to the schema; keeping it in `ShopDatabase` limits a schema change to one class. No connection pool: fine for tests, not for load.

[All decisions](README.md) · [Architecture](../architecture.md)
