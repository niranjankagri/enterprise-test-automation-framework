# ADR-005: Layered, immutable configuration

Status: accepted

- **Problem:** the same tests must run against several environments (local, qa, staging), browsers and execution modes, from a laptop, CI and Docker, without editing code.
- **Options:** (a) one properties file edited per run; (b) system properties only; (c) layered sources: system property → environment variable → environment file → defaults.
- **Decision:** (c), implemented by `ConfigLoader`, exposed through `ConfigManager.config()` as an immutable `TestConfig` record.
- **Reason:** developers use `-D` options, CI and Docker use environment variables (and secrets), and the files hold the reviewed defaults. The loader takes its sources as maps, so every precedence rule is unit-tested (`ConfigLoaderTest`) without touching global state. A record cannot change mid-run, so parallel threads can share it.
- **Trade-offs:** the configuration is fixed once per JVM; switching environment means a new run (which is what CI does anyway). Invalid values fail at start-up instead of halfway through a run, which is intended.

[All decisions](README.md) · [Architecture](../architecture.md)
