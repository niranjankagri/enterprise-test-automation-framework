# ADR-003: Framework code in `src/main`, tests in `src/test`

Status: accepted

- **Problem:** where do page objects, API clients, database helpers and utilities belong?
- **Options:** (a) everything in `src/test/java`, the usual layout of small test projects; (b) framework code in `src/main/java`, only test classes in `src/test/java`; (c) a separate framework artifact used by a test project.
- **Decision:** (b), in the `automation` module.
- **Reason:** framework code is compiled, reviewed and reused like production code, its own rules (Checkstyle, warnings as errors) apply to it, and tests stay short and readable. (c) would add versioning and release work without a second consumer.
- **Trade-offs:** test libraries (TestNG, AssertJ) are compile-scope dependencies of the module. If a second test project ever needs the framework, the module can be published as an artifact without moving code.

[All decisions](README.md) · [Architecture](../architecture.md)
