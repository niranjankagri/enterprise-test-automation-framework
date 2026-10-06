# ADR-004: SLF4J + Logback for logging

Status: accepted

- **Problem:** with tests running in parallel, log lines of different tests interleave; a log is only useful if every line says which test, thread and layer wrote it, and it must never contain secrets.
- **Options:** (a) `System.out`; (b) a logging library used directly; (c) the SLF4J facade with Logback as implementation.
- **Decision:** (c). Code logs only through the SLF4J API (Checkstyle forbids `System.out`). Logback writes INFO to the console and DEBUG to `target/logs/automation.log`; every line reads `time level [thread] [Test.method] [COMPONENT] message`, with the test name from the MDC (`TestLogContextListener`), the component from the logger's package (`LogComponent`) and the message masked (`MaskingConverter` → `SecretMasker`).
- **Reason:** SLF4J is the standard facade, so the implementation can change without touching code; the MDC and custom converters give per-test, per-layer and secret-free lines without any code in the tests.
- **Trade-offs:** console lines are long; the file log is the place for detail (DEBUG), the console stays at INFO. Third-party libraries are kept at WARN to avoid noise.

[All decisions](README.md) · [Architecture](../architecture.md)
