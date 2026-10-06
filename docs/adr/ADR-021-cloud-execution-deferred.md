# ADR-021: Cloud browser execution is deferred, and will use one provider

Status: accepted

- **Problem:** cloud browser providers (BrowserStack, Sauce Labs, LambdaTest) offer browser and OS combinations a self-hosted Grid does not, but need an account, credentials and paid minutes.
- **Options:** (a) integrate several providers behind a common interface; (b) integrate one provider now; (c) keep the `cloud` execution mode reserved, fail fast, and add one provider when a real need exists.
- **Decision:** (c). `-Dexecution=cloud` fails at start-up with "Cloud execution is not implemented; use -Dexecution=local or -Dexecution=remote (Selenium Grid)".
- **Reason:** the framework's remote execution is already proven with Selenium Grid (ADR-019), and cross-browser coverage comes from Chrome, Firefox and Edge on CI and the Grid. Code for a provider that is never run in CI would be untested code; several providers multiply that. A clear error is better than a half-working mode.
- **Trade-offs:** no Safari or mobile browsers. Adding a provider is small and local: one more `RemoteWebDriver` target in `DriverFactory` with the provider's hub URL and capabilities, credentials from CI secrets (`BROWSERSTACK_USERNAME`, `BROWSERSTACK_ACCESS_KEY`), never from files.

[All decisions](README.md) · [Architecture](../architecture.md)
