# Codex Engineering Decisions

Append material decisions with their evidence and trade-offs. Do not record credentials or sensitive report contents.

## D-001 — Keep the audited baseline and use a feature branch

- **Decision:** Start Batch A from freshly fetched `origin/main` at `9a415067fcce2bd4e658f37029775853936ab4da` on `codex/batch-a-baseline`.
- **Reason:** The fetched default branch still matched the requested audit baseline and the worktree was clean. No local commits needed to be preserved.
- **Trade-off:** Later batches must wait for the owner review/merge gate rather than depending on unmerged prior batches.

## D-002 — Use Apache Maven Wrapper in `only-script` mode

- **Decision:** Pin the repository wrapper to Apache Maven 3.9.16 using Maven Wrapper Plugin 3.3.4 and `distributionType=only-script`.
- **Provenance:** Downloaded the binary archive, `.sha512` and `.asc` from `https://downloads.apache.org/maven/maven-3/3.9.16/binaries/` and the key list from `https://downloads.apache.org/maven/KEYS`. Published SHA-512 `ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3` matched the calculated digest. GPG reported a valid detached signature from Slawomir Jaranowski, fingerprint `84789D24DF77A32433CE1F079EB80E92EB2135B1`. The archive's calculated SHA-256 is pinned as `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`; the wrapper downloads from Maven Central over HTTPS.
- **Reason:** The wrapper supplies a repeatable Maven version without requiring developers to install Maven; `only-script` avoids a binary `maven-wrapper.jar` in the repository while the generated scripts still enforce the pinned distribution checksum.
- **Verification:** Fresh-cache `mvnw.cmd -version` and Git Bash `./mvnw -version` both downloaded/verified and reported Maven 3.9.16. The wrapper ran the CI unit command successfully on Windows. GitHub Actions will validate the POSIX wrapper on Ubuntu through the draft PR.
- **Trade-off:** First use requires network access to Maven Central. Existing Docker image builds continue using their pinned Maven base image and container-local `mvn` command.

## D-003 — Migrate host GitHub Actions Maven commands to the wrapper

- **Decision:** Use `./mvnw` for host-run Maven commands in `ci.yml`, `regression.yml` and `run-suite.yml`; retain the Docker container's existing Maven command.
- **Reason:** The wrapper entry point has passed equivalent local Windows and Git Bash validation. The PR workflow must exercise the POSIX wrapper on Ubuntu before C00 can be considered verified.
- **Trade-off:** Workflow verification remains a required external gate; any failure must be fixed in a separate commit without weakening tests.

## D-004 — Synchronize the requested Desktop memory file after each chunk

- **Decision:** After each verified chunk, update the owner's same existing Desktop `MEMORY.md` with concise status, evidence, commit/PR/CI references and the next chunk, preserving all existing content. Keep personal machine paths out of repository files.
- **Reason:** The owner explicitly asked to keep this work collected in the existing memory file and to follow the same small, incremental pattern used on earlier projects.
- **Trade-off:** The Desktop file is outside the repository workspace and requires elevated filesystem access; the repository's `docs/CODEX_*` files remain the versioned project record.

## D-005 — Pin wrapper line endings

- **Decision:** Keep `mvnw` as LF and `mvnw.cmd` as CRLF with repository `.gitattributes` rules.
- **Reason:** The Windows Git configuration has `core.autocrlf=true`; the POSIX wrapper must not be rewritten with CRLF when checked out on Windows and then executed in Linux CI.
- **Verification:** Git records `mvnw` as executable (`100755`); wrapper scripts were exercised from Windows Command Prompt and Git Bash.

## D-006 - Keep Compose services internal and limit host access

- **Decision:** Publish the demo application on `127.0.0.1:8081`, leave H2 TCP 9093 available only on the Compose network, and publish Selenium Grid on `127.0.0.1:4444`.
- **Evidence:** the test runner uses `demo-app:9093` and browsers use `demo-app:8081`; runtime integration passed with H2 and Grid available internally. Container inspection showed only loopback host bindings, and no host listener accepted connections on 9093.
- **Trade-off:** local host tools can use the app and Grid through loopback, while external machines cannot reach those ports directly. Host-based H2 access is intentionally unavailable.

## D-007 - Sanitize Allure output at the reporting boundary

- **Decision:** Mask URL userinfo, sensitive values, report step/attachment labels and textual attachment content at the shared `Report` boundary. Before recording an Allure step failure, mask message, trace, actual and expected status details, then rethrow the original throwable.
- **Reason:** API filters, UI navigation and failure listeners all send their evidence through `Report`, but Allure's normal failure handling stored the original exception details before text attachments could be sanitized. URL usernames can also be sensitive independently of passwords.
- **Verification:** synthetic API/UI regression coverage inspected active Allure metadata and persisted attachments, asserted exception identity preservation, and scanned 89 text artifacts with no synthetic-secret matches.
- **Trade-off:** complete URL userinfo is hidden, while hosts, ports, paths and nonsensitive query values remain visible for diagnosis. Allure step lifecycle handling is explicit so exception metadata can be sanitized before it is stored.

## D-008 - Sanitize final Allure test and step failure details

- **Decision:** Register a `TestLifecycleListener` that masks `StatusDetails` on the top-level test and recursively nested steps at `beforeTestWrite`.
- **Reason:** The TestNG Allure adapter records an uncaught test method exception directly on the top-level result. Sanitizing only `Report.step` cannot protect this separate output path.
- **Verification:** A dedicated runner executes a real synthetic `IllegalStateException` failure, then checks the final persisted `*-result.json` after TestNG and Maven finish. It confirms the failed TestNG case and exception type remain, the Allure test and step messages remain useful in redacted form, and the synthetic username, password and query token are absent.
- **Trade-off:** Allure status details are sanitized immediately before persistence; the original throwable and TestNG failure result remain unchanged. The intentionally failing fixture is excluded from the general `full` suite and run by its dedicated verifier.

## D-009 - Coordinate account deletion with session lifecycle

- **Decision:** Use a fair read/write lock inside each `AuthService`. Login and token authentication share read access; account deletion holds write access while deleting the row and removing every session for that username.
- **Reason:** A token map removal performed after an uncoordinated database delete can miss a concurrent login that registers its token afterward. The shared lock makes the delete/revoke operation exclusive with both login and authentication while retaining concurrent read-side requests.
- **Verification:** API regression tests confirm a token works before deletion and is rejected afterward, deleted credentials cannot log in again, and a latch-started login/authentication/delete race leaves no usable token. A deterministic adjustable-clock test confirms 401 at the exact expiry deadline. PR #17 Actions run `39421373220` passed all jobs.
- **Trade-off:** A deletion briefly blocks logins and auth checks for all accounts handled by that `AuthService` instance. An HTTP request already authorized before deletion can finish in flight; requests authenticated after deletion completes are rejected.

## D-010 - Inject the auth clock for deterministic expiry tests

- **Decision:** Keep system UTC and the existing one-hour token lifetime as `AuthService` production defaults, and allow an explicit `Clock` and `Duration` to be supplied to the service constructor.
- **Reason:** Expiry should be tested at the deadline without sleeping or introducing time-based flakiness. The same `Clock` now controls issuance and validation.
- **Verification:** `AuthServiceExpiryTest` advances an adjustable clock from before the deadline to the exact deadline and observes 200 then 401 through the router's HTTP boundary.
- **Trade-off:** The constructor adds an injection seam, while normal application startup continues to use the existing one-argument constructor and unchanged defaults.

## D-011 - Reserve order stock with a conditional update

- **Decision:** Decrement inventory with one conditional SQL update that requires `active = TRUE` and `stock >= quantity`; require exactly one affected row. Treat a zero-row result as a current-state `409` conflict and check affected rows when restoring stock.
- **Reason:** The prior read-check-update sequence allowed two orders to observe the same inventory and both succeed. The conditional update serializes at the product row and rechecks available stock after waiting, while staying within the existing SQL transaction and error conventions.
- **Verification:** A deterministic H2 contention test locks the shared product row until both API requests reach their reservation updates. It reproduced two successful responses before the fix and then passed with one `201`, one `409`, nonnegative final stock, one order, and rollback of the losing order's earlier line reservation. Local integration suite and Actions run `39423169979` passed.
- **Trade-off:** A competing order may now receive `409` after initially passing the friendly stock read; the response reports the current remaining units. The existing transaction remains responsible for rolling back all prior order work.

## D-012 - Keep the pure unit suite independent of application lifecycle

- **Decision:** Do not register `DemoAppLifecycle` in `unit.xml`; retain the listener in suites that exercise API, UI, database or cross-layer behavior. Guard the unit suite configuration with an offline regression test.
- **Reason:** The unit suite selects framework-only tests and should not start the demo application or H2 based on the local environment's `app.autostart=true` setting.
- **Verification:** Before the change the 34-test unit suite passed but started the app and H2. After the change, 35 tests passed with both ports closed before and after, no application process running, and local `app.autostart=true`. Actions run `39424239632` passed all jobs.
- **Trade-off:** Suites that call API, UI or database services continue to use the existing lifecycle listener and startup behavior; only the unit suite omits it.

## D-013 - Validate order quantities before narrowing numeric values

- **Decision:** Accept only integral JSON values that fit in the supported Java `int` range and are greater than zero. Return the existing client validation response before writing order or stock records when the value is invalid.
- **Reason:** `JsonNode.intValue()` narrows larger integers. A value such as `4294967297` therefore became `1` and was accepted, including in a mixed-line order.
- **Verification:** Regression tests reproduced two accepted orders before the fix. After the fix, the API suite passed 74 tests with no failures, errors or skips; mixed-line rollback and unchanged inventory/order state are asserted. PR Actions run `39436856557` passed the API, quality, dependency review, Chrome smoke and report jobs.
- **CI decision:** Add the existing API suite as a PR job and require it before combined report generation. This makes order API regressions execute on every PR without adding a new framework or duplicating existing tests.
- **Trade-off:** A quantity at `Integer.MAX_VALUE` is a valid positive integer, but receives the existing insufficient-stock conflict when inventory is lower. This keeps numeric validation separate from available-stock business rules.

## D-014 - Parse boolean configuration explicitly

- **Decision:** Accept only case-insensitive `true` or `false` after trimming for `headless`, `app.autostart`, and the demo application's `DB_ALLOW_REMOTE`. Preserve each setting's existing default when unset or blank. Error messages identify the key and never repeat invalid input.
- **Reason:** `Boolean.parseBoolean` silently maps every value other than `true` to `false`. This can unexpectedly show a browser window, prevent local app startup, or silently disable the documented remote H2 connection.
- **Verification:** Regression-first unit runs reproduced the invalid-value behavior for all three settings. The final unit suite passed 41/41 with Checkstyle 0; synthetic invalid strings were absent from generated Allure results, logs and Surefire reports. Actions run `39439648609` passed all jobs.
- **Trade-off:** `DemoApp.Options.fromEnvironment(Map)` adds a testable map-based resolver; normal application startup continues to use `System.getenv()` through the existing no-argument method.

## D-015 - Treat repository configuration as the source of truth for coverage documentation

- **Decision:** Derive dependency versions, suite/group descriptions, CI execution paths and Compose networking guidance from the current POMs, suite XML, workflows and Compose file. Use the coverage matrix for requirement-level evidence; do not use a fixed test count as a coverage claim. Use the Maven Wrapper for host commands and preserve the container image's Maven command inside Docker.
- **Reason:** Group overlap and data providers change the number of executed test cases, while a count does not establish that a requirement, route/role combination, branch or browser was covered.
- **Verification:** Root POM, suite XML, workflows and Compose bindings were compared with the updated documentation; `git diff --check` passed. PR Actions run `39443489388` passed all jobs on documentation commit `5284f992c369087f53fc510353a4d30ce4ebd4bc`; final evidence commit `4588e482f19ebc019461fa7d55fa5fed958e3124` also passed all jobs in run `39444011311`.
- **Trade-off:** Historical runtime observations may remain when dated and labeled as machine-dependent; stale case totals and unsupported claims are removed.
