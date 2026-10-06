# ADR-001: Multi-module Maven build

Status: accepted

- **Problem:** the framework needs one place for versions, and later a second module (the application under test, see ADR-002).
- **Options:** (a) one single-module project; (b) a parent POM with modules.
- **Decision:** a parent POM (`pom.xml`) with the `automation` module.
- **Reason:** all versions live in the parent's `dependencyManagement`, modules declare only what they use, and new modules can be added without restructuring.
- **Trade-offs:** one extra directory level (`automation/src/...` instead of `src/...`).

[All decisions](README.md) · [Architecture](../architecture.md)
