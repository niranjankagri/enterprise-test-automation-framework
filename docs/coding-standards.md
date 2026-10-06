# Coding standards

Enforced automatically where possible (Checkstyle in every build, compiler warnings as errors in CI); the rest is checked in code review.

## Structure

- Tests describe behaviour; Selenium, HTTP and SQL live in the framework (`automation/src/main/java`).
- Dependencies point downwards only: tests → pages/services/queries → building blocks → driver/config.
- One responsibility per class; no "utility god classes". Domain knowledge of the application lives in `ui.pages`, `api.services` and `ShopDatabase`, nowhere else.
- Each locator is declared once (`private static final By`, `TestId.of(...)`), in its page or component.
- No duplicated API logic: requests go through `ApiClient` and a service.

## Waiting

- **Never** `Thread.sleep` (Checkstyle) and **never** implicit waits (Checkstyle). Wait for a condition with `WaitUtils` / page `waitUntilLoaded()`.
- Read lists and toasts inside a wait: they re-render.

## State and parallelism

- No mutable static state except thread-confined (`ThreadLocal`) or concurrent structures (`ConcurrentHashMap`, atomics).
- Configuration and data objects are immutable records.
- Tests never depend on each other or on execution order; each creates and removes its own data.

## Errors

- No empty `catch` blocks (Checkstyle). Either handle, rethrow with context, or log why ignoring is correct.
- Fail fast with a message that says what to do (`Unknown browser 'x'. Valid values: chrome, firefox, edge`).
- Clean-ups and evidence collection never throw over the real test result.

## Secrets

- No credentials, tokens or keys in code, configuration files or Git; real values come from environment variables / CI secrets (`.env.example`).
- Objects holding secrets mask them in `toString()`; logs and reports mask password/token fields, bearer headers and secret-named test parameters.

## Tests

- Name tests as behaviour: `duplicateEmailGives409`, `viewerCannotPlaceOrders`.
- Arrange–act–assert, separated by blank lines; AssertJ with `.as(...)` where the intent is not obvious.
- Every test has groups: a layer (`unit`, `platform`, `api`, `ui`, `db`, `integration`, `e2e`) and a purpose (`smoke`, `sanity`, `regression`, `security`) where it applies.
- Data-driven cases: first column is a readable case name.

## Style

- Java 17 language level; records, switch expressions, `var` only where the type is obvious.
- No star imports, no unused imports, braces always, one statement per line (Checkstyle).
- Javadoc on public classes explains *why*, not only *what*; comments on non-obvious decisions.
- Logging through SLF4J; no `System.out` (Checkstyle).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `ci:`, `build:`, `chore:`), with a body explaining the reason for the change; see [CONTRIBUTING.md](../CONTRIBUTING.md).
