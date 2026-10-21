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

## D-016 - Complete C09–C11 as one Batch E review PR

- **Decision:** After PR #18 merged, create `codex/batch-e-quality-release` from the fetched `origin/main` and keep C09, C10 and C11 in one owner-review PR. Preserve prior batch history and revise only the current execution status.
- **Reason:** The owner explicitly authorized autonomous completion of the remaining coverage/release scope in one PR, superseding the old Batch D stop checkpoint.
- **Verification policy:** Establish clean-start suite/tooling baselines before implementation; keep each logical fix independently tested and committed on the same branch; only mark the PR ready after its required checks and final validation reach terminal status. Never merge, enable auto-merge or deploy.
- **Trade-off:** The PR will be larger than earlier remediation PRs. The coverage matrix and progress log will link each requirement to its own suite and evidence to keep the review traceable.

## D-017 - Serialize order lifecycle transitions and use a stable product lock order

- **Decision:** Lock the order header while changing status or deleting it, condition the transition on its previous state, and verify affected-row counts. Restore stock only for the winning transition, locking order lines by ascending product ID.
- **Reason:** Concurrent cancellation, deletion or cancellation-plus-deletion could each observe a placed order and restore the same stock twice. Overlapping orders with reversed line order could also acquire product locks in opposite orders.
- **Verification:** H2 contention tests cover two cancellations, two deletions, a cancellation/deletion race and reversed line order. They assert bounded completion, final status/deletion, order lines and stock. Integration suite passed 28/28 at `7a7b69b`; the PR API/order run `39456674788` passed.
- **Trade-off:** A losing lifecycle request receives the existing conflict/not-found response after the lock holder commits. Multi-product operations acquire locks in product-ID order, preserving the existing database and transaction design.

## D-018 - Mask screenshot values temporarily and preserve browser failure behavior

- **Decision:** Add temporary opaque visual overlays over visible password inputs and explicitly marked `[data-sensitive]` elements before capture. Do not replace DOM nodes, alter their values or remove their descendants. Remove overlays in `finally`; omit the screenshot if masking, capture or restoration cannot be verified. Log exception classes only so browser diagnostics do not copy page content.
- **Reason:** Textual Allure redaction does not alter pixels already rendered in a screenshot. Replacing `textContent` on a marked container can destroy nested controls and event handlers, so pixel protection must leave the page content intact.
- **Verification:** `ScreenshotUtilsTest.masksSensitivePageWithReversibleOverlaysAndRestoresAfterCaptureFailure` checks overlay pixels, original container/control identity and markup, an interactive click handler during masking, restoration after successful and failed capture, and omission when masking setup fails. The test is in `platform`, `smoke` and `regression` groups so PR Chrome smoke and the nightly browser/Grid matrix exercise it. This protects only password and explicitly marked regions; unmarked sensitive pixels are not universally redacted.
- **Accessibility decision:** Give modal dialogs an accessible title, trap Tab navigation, close with Escape and return focus to the opener. Automated login/customer/checkout checks cover labels, visible focus and dialog behavior; no screen-reader conformance claim is made.


## D-019 - Measure Java coverage by module and tested source revision

- **Decision:** Pin JaCoCo 0.8.15, instrument the automation test JVM and both production modules, merge only non-empty artifacts whose manifests identify the same workflow source SHA, then publish separate `demo-app` and `automation` XML/HTML reports. Preserve separate external-container collection for nightly Grid execution.
- **Reason:** The framework JVM executes demo-app classes during API/integration tests, while the Grid app container is a separate JVM. Treating them as one execution source would miss code or risk mixing revisions.
- **Verification:** PR CI run `39463151404` verified four same-SHA inputs and real production classes. The tested merge SHA `3cede33dffde81c9459a57ff914248571bf14329` measured demo-app lines 912/975 and branches 262/334; automation lines 1171/1449 and branches 224/378. The report verifier rejects absent classes or empty counters.
- **Trade-off:** No threshold is set before the requirement denominator and baseline are reviewed. Percentages describe executed production code only and do not imply requirement or browser coverage.

## D-020 - Run CodeQL in the repository workflow with fork-safe upload

- **Decision:** Add a pinned-major CodeQL v4 advanced workflow for Java/Kotlin, extended queries, manual Maven compilation, least-scoped analysis permissions, and avoid result upload for fork pull requests.
- **Reason:** Repository metadata showed CodeQL default setup was not configured. The workflow makes source analysis part of this PR without changing repository settings or exposing a write token to fork code.
- **Verification:** C10 CodeQL run `39463151680` passed on `62427b353eb6aba3cc96b3371473c80a0f83b6a5`; final C11 run [39469980683](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39469980683) also passed on the final source, with zero open CodeQL alerts.
- **Trade-off:** GitHub native secret scanning and push protection are enabled, but their alerts are not a downloadable Actions artifact. Dependabot security updates remain disabled by repository configuration; no settings were changed.

## D-021 - Keep nightly container coverage separate from test-JVM coverage

- **Decision:** Copy the same pinned JaCoCo agent into the demo-app runtime image and attach it only in the Docker/Grid nightly workflow; collect the app process data separately from automation test JVM data, then validate and report module execution on matching source revisions.
- **Reason:** CI API/integration tests may cover in-process app classes, but a Grid container runs the app in another JVM and cannot write to the automation workspace without an explicit bind mount and agent destination.
- **Verification:** Docker image build and Compose configuration passed locally; PR coverage passed; final nightly run [39470084784](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39470084784) validated separate Grid/container coverage on source SHA `bc1c1ec87e6c7cb12c68fa6383a2f4134bfdd98e` and stopped the containers successfully.
- **Trade-off:** The coverage merge is valid only for artifacts produced from the same source revision and compatible JaCoCo format; nightly container execution is reported separately from PR coverage.

## D-022 - Preserve the browser setup exception if cleanup also fails

- **Decision:** When driver configuration fails, retain that setup exception as the thrown failure. Attempt `quit()` and add any cleanup exception as suppressed rather than replacing the original failure.
- **Reason:** A browser setup error is the actionable cause; a failing quit must not obscure it or make diagnostics misclassify the failure.
- **Verification:** `DriverFactoryTest.setupFailureRemainsPrimaryWhenQuitAlsoFails` uses a deterministic `WebDriver` proxy whose configuration and quit methods both fail. The unit suite passed 43/43 and the final full suite passed 213/213 at source SHA `bc1c1ec87e6c7cb12c68fa6383a2f4134bfdd98e`; PR CI passed on merge SHA `fdaaab3817c2690d092a80731ef520da00a71ffe`.
- **Trade-off:** Cleanup remains best-effort after setup failure; the original cause stays primary and the cleanup failure remains available as suppressed diagnostic evidence.

## D-023 - Mask password values in TestNG's retained provider parameters

- **Decision:** Wrap the negative-login provider's password column in `SensitiveValue`, whose `toString()` returns a fixed mask. Unwrap only at the UI call site so the application still receives the original test value.
- **Reason:** Allure parameter sanitization did not protect the built-in TestNG/Surefire retained result parameters. The final artifact scan confirmed raw negative-login passwords in those reports.
- **Verification:** `TestDataTest.loginNegativeProviderMasksPasswordsInTestReports` checks every provider row remains a masked object; final unit/UI/full tests pass, and the retained Allure, Surefire and log scan found no checked demo or synthetic credential patterns. PR quality and nightly browser artifacts passed on source SHA `bc1c1ec87e6c7cb12c68fa6383a2f4134bfdd98e`.
- **Trade-off:** The wrapper deliberately masks its string representation; tests that need to submit the password must explicitly access `value()` at the UI boundary.

## D-024 - Require executed JaCoCo coverage in every production module

- **Decision:** Require positive covered line and branch counts in each module report and in the designated production class (`OrderApi` for `demo-app`, `ApiClient` for `automation`). Add standard-library Python regressions for empty execution, module totals, and designated-class line/branch execution.
- **Reason:** A report can list production classes and measurable missed counters while recording zero executed code; class presence alone does not prove coverage data belongs to the intended production path.
- **Verification:** `python -B -m unittest discover -s scripts/tests -v` passed four verifier tests. The strengthened verifier accepted actual local JaCoCo reports generated from the Chrome smoke execution; PR and nightly coverage jobs now run the verifier regressions before checking their reports.
- **Trade-off:** A module or designated class with no covered branch fails the gate even if lines ran. This is intentional for the current modules, whose designated classes contain branch decisions; no percentage threshold is introduced.
## D-025 - Simulate browser request failures through a test-only Fetch wrapper

- **Decision:** Install a bounded, route-and-method-specific Fetch wrapper in the browser test session. It can gate a request until an explicit release, return a synthetic HTTP error or throw a synthetic network error; restore the original Fetch function in test cleanup.
- **Reason:** UI loading and order-failure behavior must be reproducible without external outages or race-based timing. A test-only browser hook preserves the production app contract and makes delayed/pending states directly observable.
- **Verification:** H01 regressions cover customer/catalog load and retry, failed order no-write state plus retry, and an expired-session 401. Clean Chrome smoke on `3a322a2a4bc1264b9a81f37d9c3ada48495063d1` passed 27/27 with no failures, errors or skips; Checkstyle reported zero violations.
- **Limit:** The tests exercise application behavior after Fetch failures; they do not simulate every browser, proxy or operating-system network failure mode. Remote PR CI is pending.

## D-026 - Validate monetary values against persisted DECIMAL bounds before writes

- **Decision:** Reject product prices that exceed `DECIMAL(10,2)` or require non-zero precision beyond cents; accept additional trailing zeroes because they do not require rounding. Reject order totals above `DECIMAL(12,2)` before reserving inventory.
- **Reason:** H2 previously rounded a sub-cent product price and raised an internal server error for values outside the schema range. Those are invalid client values and must not leave partial order or stock changes.
- **Verification:** H02 regressions reproduced the defects before the change. On commit `bac7e20e1245d3fdb0b97fc7c2d0861648480799`, API passed 85/85 and integration passed 37/37, with zero failures/errors/skips and Checkstyle 0. Draft PR CI [39540811264](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811264) and CodeQL [39540811258](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811258) passed on PR head `c3f33954e4df9e4b5c58d80a3b14ae0e348ed35c`.
- **Trade-off:** Monetary limits intentionally mirror the current H2 schema; changing those limits requires a coordinated schema and API contract change.
