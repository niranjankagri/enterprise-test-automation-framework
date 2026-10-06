# ADR-017: REST Assured for API testing

Status: accepted

- **Problem:** API tests must send any request (valid, malformed, unauthenticated), inspect status, headers and bodies, validate JSON schemas, and plug into logging and reporting with secrets masked.
- **Options:** (a) the JDK `HttpClient` with a JSON library; (b) REST Assured; (c) a generated client from an API specification.
- **Decision:** (b), behind the framework's own `ApiClient` and service classes (ADR-010).
- **Reason:** REST Assured is the de-facto Java API testing library: readable request building, response access, JSON schema validation (`json-schema-validator`) and filters, which the framework uses for one log line per call (`ApiLoggingFilter`) and report steps with masked evidence and the correlation id (`ReportingApiFilter`). A generated client would refuse to send the invalid requests that negative tests need.
- **Trade-offs:** its fluent static API invites global state (`RestAssured.baseURI`); the framework avoids it: `ApiClient` is immutable and builds every request from scratch, so parallel tests share nothing. JSON mapping uses the framework's own Jackson mapper instead of REST Assured's auto-detection.

[All decisions](README.md) · [Architecture](../architecture.md)
