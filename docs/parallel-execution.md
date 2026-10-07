# Parallel execution

## How to run

```bash
mvn clean test                                   # full suite, parallel=classes, 2 threads (defaults)
mvn clean test -Dthreads=4                       # 4 threads
mvn clean test -Dparallel=none                   # serial, e.g. to debug one failure
mvn clean test -Dsuite=regression -Dthreads=4    # any suite, any thread count
```

Parallel mode and threads come from configuration (`parallel`, `threads`; also `PARALLEL`, `THREADS` environment variables) and are applied to every suite by `ExecutionSettingsListener`. The suite XML files only describe *what* runs.

## Measured (local, headless Chrome, 158 tests)

| Mode | Wall time (Maven) | Tests |
|---|---|---|
| serial (`-Dparallel=none`) | ~242 s | 169 passed |
| `classes`, 2 threads | ~171 s | 169 passed |
| `classes`, 4 threads | ~148 s | 169 passed |

Measured from a clean clone on a laptop (Chrome, headless), 2026-10-07; Maven start-up and compilation included.

The gain flattens at 4 threads because parallelism is per class and the largest data-driven classes (customer management with 8 CSV cases, the 8-product catalogue) set the critical path. Splitting those classes, or more Grid capacity ([grid.md](grid.md)), shortens it further.

## Why it is safe

| Shared thing | How it is isolated |
|---|---|
| Browser | `DriverManager` holds one `WebDriver` per thread (`ThreadLocal`); every UI test gets a fresh browser |
| Test data | unique emails/SKUs/usernames per call (`RandomDataGenerator`: run id + atomic counter) |
| Clean-up | `CleanupRegistry` is per thread |
| API | `ApiClient` is immutable and builds every request itself (no REST Assured global state); the token cache is a `ConcurrentHashMap` |
| Configuration | immutable `TestConfig` record, created once (holder idiom) |
| Database | one JDBC connection per query |
| Assertions on shared lists | check only the seeded catalogue (`ProductPage.catalogueRows()` ignores `TST-` products) or the test's own rows; never "the 5 most recent orders" |
| Logs | every line carries the thread, the test name (`%X{test}` via `TestLogContextListener`) and the component (`[API]`, `[UI]`, `[DB]`...) |

## `classes` (default) and `methods`

Both modes are supported. With `parallel=methods` several threads run tests of the same class instance at the same time, so per-test state cannot live in plain fields: the two classes that set state in `@BeforeMethod` (`OrderApiTest`'s customer and products, `PurchaseJourneyTest`'s buyer) keep it in `ThreadLocal`s, which works because TestNG runs a `@BeforeMethod` on its test's thread. Shared fields set once in `@BeforeClass` (`ShopDatabase`) are read-only and safe in both modes.

| Mode, 4 threads | Full suite, same session (2026-10-07) |
|---|---|
| `methods` | 169/169 twice (~187 s, ~270 s) |
| `classes` | 169/169 (~192 s) |

`methods` gave no measurable gain here (timings vary with machine load, and every UI test starts its own browser in either mode), so `classes` stays the default: fewer threads touch one class's data at once, which keeps failures easier to read. Switch per run with `-Dparallel=methods`.

## Retry strategy

Retry transient infrastructure failures; never retry real application failures or assertion failures to make the build green.

- `RetryTransformer` attaches `RetryAnalyzer` to every test.
- `RetryAnalyzer` retries only when `TransientFailures.isTransient(...)` says the failure (or one of its causes) is infrastructure: session not created, browser gone/unreachable, connection refused/reset, socket timeouts. At most `retry.count` times (default 1).
- Never retried: `AssertionError` (also when caused by a network error), wait timeouts (the application did not reach the expected state), missing elements, unexpected HTTP statuses.
- Every retry is logged with its cause, and the retried attempt is reported as skipped, so retries stay visible.
