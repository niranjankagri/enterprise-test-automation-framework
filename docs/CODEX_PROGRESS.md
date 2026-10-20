# Codex Remediation Progress

Updated at each verified chunk. Preserve earlier evidence when updating this file.

## Batch A record: repository baseline and coding harness

- **Status:** C00 verified complete and merged after owner review of PR #15. Batch B progress follows.
- **Branch:** `codex/batch-a-baseline`, based on freshly fetched `origin/main`.
- **Baseline SHA:** `9a415067fcce2bd4e658f37029775853936ab4da`.
- **Implementation result SHA:** `ac89f577f78b16c248d0f65228de337fc2627088`.
- **Commit / push:** `ac89f57` (`test(build): add verified Maven wrapper and C00 records`), pushed to `origin/codex/batch-a-baseline`.
- **PR:** [#15](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/15), targeting `main`; merged after owner approval.
- **GitHub Actions:** [CI run 39409762009](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39409762009) completed successfully for `ac89f577f78b16c248d0f65228de337fc2627088`: compile/unit quality gate, dependency review, Chrome smoke and Allure report all passed.

### Verified pre-change state

- `git fetch --prune origin` succeeded after a per-command `safe.directory` override and elevated filesystem access. Fetched `origin/main` remained `9a415067fcce2bd4e658f37029775853936ab4da`; the worktree was clean.
- No `AGENTS.md` or `docs/CODEX_*` records existed in the repository or checked parent directories.
- The root POM aggregates two modules, `demo-app` and `automation`; target Java release is 17. Existing workflows use GitHub Actions PR, main, nightly and reusable jobs.
- Java 27 and IntelliJ-bundled Maven 3.9.16 are available. No wrapper existed. Docker CLI 29.8.2 is installed, but no Docker engine is running. GitHub CLI 2.102.0 is installed, but its saved token is invalid.
- Unit baseline command (using the existing local Maven cache because this sandbox resolves the default cache to unwritable `C:\.m2`; the account-specific cache path is omitted from this public repository record):
  `mvn.cmd -B -ntp -Dmaven.repo.local=<existing-local-cache> clean test -Dsuite=unit -Dmaven.compiler.failOnWarning=true`
  Result: **BUILD SUCCESS; 32 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 violations.**
- The baseline confirms the C05 issue: `automation/src/test/resources/suites/unit.xml` includes `DemoAppLifecycle`; `automation/src/main/resources/config/local.properties` enables `app.autostart=true`. The unit suite started the app on 8080 and H2 on 9092; both ports were closed after the suite.

### C00 work and verification

- Apache Maven Wrapper Plugin 3.3.4 generated `only-script` wrapper files. Maven 3.9.16 is pinned in `.mvn/wrapper/maven-wrapper.properties` with SHA-256 `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`.
- Provenance: downloaded the Apache-hosted Maven 3.9.16 binary zip, `.sha512`, `.asc` and Maven `KEYS`; published SHA-512 matched the calculated digest; GPG returned `GOODSIG` and `VALIDSIG` for the archive. The pinned SHA-256 is `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`. The wrapper itself successfully downloaded the pinned distribution and reported Maven 3.9.16 in a fresh temporary cache on Windows and Git Bash.
- Wrapper-based CI unit command: **BUILD SUCCESS; 32 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 violations.**
- Wrapper-based main-workflow command `mvnw.cmd -B -ntp -Dmaven.repo.local=<existing-local-cache> clean package -Dsuite=unit -Dmaven.compiler.failOnWarning=true`: **BUILD SUCCESS; 32 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 violations; both module JARs packaged.** Maven Shade emitted its existing module-info/overlapping license-resource warnings; the compiler warning gate passed.
- `.github/workflows/ci.yml`, `regression.yml` and `run-suite.yml` now call `./mvnw`; Docker's container-local `mvn` command is unchanged. Local wrapper tests passed, and the Ubuntu PR workflow passed all jobs on the implementation commit.
- Local Maven wrapper tests use a task-specific temp wrapper cache and explicit `maven.repo.local` because of this sandbox's profile mapping; these paths are not committed.
- Final C00 diff review and `git diff --cached --check` passed. The final artifact scan found no bearer credentials, URL userinfo, sensitive query values or JSON credential fields in local Allure results, logs or TestNG reports. The staged-file scan found no personal machine path, credential assignment or private key.
- Docker Compose validation is **BLOCKED / NOT EXECUTED** because the Docker daemon is unavailable. C00 does not change Docker configuration; this is recorded for the later Docker security chunk.
- C00 reached its owner review gate and was later merged after approval. Batch B began only after the owner confirmed the merge; see the C01 record below.

## Chunk ledger

| Chunk | Status | Evidence / result |
|---|---|---|
| C00 | Verified complete and merged | Commit `ac89f577f78b16c248d0f65228de337fc2627088`, PR #15 merged; all recorded C00 CI checks passed. |
| C01 | Verified complete | Commit `8a4e579fbce015098c22a55413fe78781432aac5`; local Compose integration passed and CI run `39414072615` passed all jobs. |
| C02 | Verified complete | Commit `6d6c8b8b4af2e60fc363b9ac4e525bc38df42d59`; local suite and artifact scan passed; CI run `39415956405` passed all jobs. |
| C03 | Verified complete | Commit `ed2fb00507caaeaed9b4428f15bdc509aef34b76`; local API suite 59/59 passed; Actions run `39421373220` green; PR #17 draft. |
| C04-C11 | Pending | Continue C04 after C03's implementation and progress-record CI gates passed. |

## Blockers and required follow-up

- At the C00 baseline, Docker Compose validation was blocked because the daemon was unavailable; C01 has since passed local Compose config and integration validation (see the Batch B record).
- The saved GitHub CLI token was invalid, but the existing Git credential helper provided a valid credential without displaying it; the branch push, PR creation and Actions inspection all succeeded.
- At C00 completion Batch B was pending review of PR #15. The owner later confirmed the merge and Batch B is now active.

## Batch B - C01 Docker network security (verified complete)

- **Starting point:** owner confirmed PR #15 was merged. `git fetch --prune origin` showed `origin/main` at `28cddd91e9d059e32550e438367ea9352dc656b7`; `codex/batch-b-security` was created from that commit with a clean worktree.
- **Confirmed issue:** `docker/docker-compose.yml` published demo-app ports 8081 and H2 9093 on all host interfaces and published Selenium Grid 4444 on all host interfaces. Compose service-to-service traffic uses the internal network and `demo-app:9093`.
- **Files changed for C01:** `docker/docker-compose.yml`, `docs/docker.md`.
- **Change:** bind app 8081 and Grid 4444 to `127.0.0.1`; remove H2 host publication while preserving the internal service URL; update networking and troubleshooting docs.
- **Validation:** `docker compose -f docker/docker-compose.yml config --quiet` passed. Parsed Compose JSON assertions confirmed app 8081 and Grid 4444 are loopback-bound and app 9093 has no host publication. Runtime container inspection confirmed the same mappings; host connection to 9093 was refused. Existing cross-layer integration tests successfully used `demo-app:9093` and Selenium Grid internally.
- **Compose integration command:** `docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit --exit-code-from tests`, with `SUITE=integration`, `BROWSER=chrome`, `THREADS=2`; result `COMPOSE_TEST_EXIT=0`. `automation/target/surefire-reports/TestSuite.txt`: 22 tests, 0 failures, 0 errors, 0 skipped. `docker compose -f docker/docker-compose.yml down --remove-orphans` cleanup result: `COMPOSE_CLEANUP_EXIT=0`; no project containers remained.
- **Review:** `git diff --check` passed. No new test was needed for this configuration-only fix; the existing Compose integration suite verified application, H2, Grid and UI/API/database connectivity. GitHub Actions run [39414072615](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39414072615) completed successfully on commit `8a4e579fbce015098c22a55413fe78781432aac5`: quality gate, dependency review, Chrome smoke and Allure report all passed.
- **Commit / push:** `8a4e579fbce015098c22a55413fe78781432aac5` (`fix(docker): restrict exposed service ports`), pushed to `origin/codex/batch-b-security`.
- **Draft PR:** [#16](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/16), targeting `main`.
- **Memory record:** C01 evidence was appended to the existing Desktop `MEMORY.md` with its prior byte prefix verified intact.

## Batch B - C02 Allure URL redaction (verified complete)

- **Confirmed issue:** `SecretMasker` hid URL passwords but left userinfo usernames exposed. Allure step and attachment labels/text passed through `Report` without a final mask. A synthetic navigation exception also confirmed that Allure persisted raw URL userinfo/query secrets in step status details and stack traces.
- **Output path review:** direct Allure steps and attachments are centralized in `Report`; API evidence uses `ReportingApiFilter`, UI navigation uses `BasePage`, and failure URL/page-source/error evidence uses `ReportEvidenceListener`. The API/UI/error paths therefore share the report boundary.
- **Files changed for C02:** `automation/src/main/java/com/enterprise/automation/reporting/Report.java`, `automation/src/main/java/com/enterprise/automation/reporting/SecretMasker.java`, `automation/src/test/java/com/enterprise/automation/tests/platform/SecretMaskingTest.java`, `automation/src/test/java/com/enterprise/automation/tests/platform/ReportRedactionTest.java`.
- **Change:** mask complete URL userinfo before generic key/value handling; mask every Allure step/attachment name and textual attachment body. Record masked Allure exception status details while rethrowing the original throwable unchanged.
- **Regression coverage:** synthetic API/UI URLs with userinfo and sensitive query values cover step names, text/JSON/HTML attachments, PNG labels and a failing navigation step. The test inspects the active Allure result metadata and persisted attachment bodies; it verifies safe host/path/page details remain and the same exception object is rethrown.
- **Validation command:** Windows wrapper invocation `mvnw.cmd -B -ntp -Dmaven.repo.local=<existing-local-cache> clean test -Dsuite=unit -Dmaven.compiler.failOnWarning=true`; **BUILD SUCCESS; 34 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 violations; exit 0.** `git diff --check` passed.
- **Retained-artifact scan:** scanned 89 local text artifacts under `automation/target`; 0 occurrences of the six synthetic URL secrets. The Allure result record contained the expected redacted URLs and labels with useful safe details intact.
- **Review note:** the failing-path regression first reproduced the raw exception-details leak. A lifecycle implementation correction was verified by the passing suite and post-run scan. No real credentials or URL data were used.
- **Review:** `git diff --check` passed; no machine-specific paths or real credentials were recorded. GitHub Actions run [39415956405](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39415956405) completed successfully on commit `6d6c8b8b4af2e60fc363b9ac4e525bc38df42d59`: quality gate, dependency review, Chrome smoke and Allure report all passed.
- **Commit / push:** `6d6c8b8b4af2e60fc363b9ac4e525bc38df42d59` (`fix(reporting): redact sensitive URLs from Allure output`), pushed to `origin/codex/batch-b-security`.
- **Draft PR:** [#16](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/16), targeting `main`.
- **Memory record:** C02 evidence was appended to the same Desktop `MEMORY.md`; its original byte prefix was verified intact.
- **Next:** pause at the Batch B owner review gate. Keep PR #16 in draft; start Batch C only after the owner approves/merges Batch B.

### C02 pre-merge addendum - test-level Allure failure details

- **Confirmed gap:** `Report` already sanitized step failure details, but Allure TestNG copied an uncaught test exception into top-level `TestResult.statusDetails` and wrote it without passing through that step boundary.
- **Change:** register `AllureFailureRedactionListener` with the Allure lifecycle and mask message, trace, actual and expected fields on the test result and all nested steps immediately before Allure persists the result. The listener changes report metadata only; it does not alter `ITestResult` or the thrown exception.
- **Regression:** a dedicated suite runs one intentionally failing TestNG test with synthetic URL username, password and `access_token` value. The verifier waits for Maven/TestNG to finish, checks Surefire recorded exactly one `IllegalStateException` failure, reads the final `*-result.json`, checks the preserved redacted test and step messages, and rejects any synthetic values in that JSON. `full.xml` excludes only this dedicated fixture group so nightly runs stay meaningful.
- **Validation:** `python scripts/verify_allure_failure_redaction.py` passed on Windows and in CI; it observed the TestNG failure and Allure status `broken`, confirmed test-level and step-level details were retained in redacted form, then removed generated artifacts. `mvnw.cmd -B -ntp clean test -Dsuite=unit -Dmaven.compiler.failOnWarning=true` passed: 34 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0; exit 0. Staged and working-tree `git diff --check` passed.
- **Implementation commit / push:** `aa9166e3c63f49afa77fb0414e1ef435af065001` (`fix(reporting): redact test-level Allure failures`), pushed to `origin/codex/batch-b-security`.
- **GitHub Actions:** [run 39419116576](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39419116576) completed successfully on `aa9166e3c63f49afa77fb0414e1ef435af065001`: verifier, quality gate, dependency review, Chrome smoke and Allure report all passed.
- **Draft PR:** [#16](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/16) remains open and draft, targeting `main`.
- **Review gate:** PR #16 remains draft; do not merge without owner review.

## Batch C - C03 session revocation on user deletion (verified complete)

- **Starting point:** after the owner confirmed PR #16 merged, `git fetch --prune origin` placed `origin/main` at `e4b2f7a9118030906d6c6a0c0f886f6b7c60d10a`. The clean `codex/batch-c-correctness` branch was created from that SHA.
- **Confirmed issue:** `UserApi.delete` removed the `app_users` row without touching the in-memory `AuthService.sessions` map. Existing tests only verified that later login failed; they did not check active bearer-token revocation, expiry boundary behavior or delete/auth races.
- **Change:** coordinate login and token checks with deletion under a fair read/write lock. Login and authentication can share read access; deletion exclusively removes the row and the user's sessions, preventing a concurrent login from leaving a usable token. Expiration uses an injected clock and rejects tokens at the deadline; production defaults remain system UTC and one hour.
- **Regression coverage:** `UserApiTest` verifies an issued token works before deletion, fails after deletion, and cannot be recreated through login. A latch-started race runs token authentication, fresh login and deletion concurrently, then checks every returned token fails after deletion. `AuthServiceExpiryTest` uses an adjustable clock and a loopback HTTP server to verify success before expiry and 401 exactly at expiry. Cleanup tolerates the account already being deleted.
- **Files changed:** `demo-app/src/main/java/com/enterprise/demoapp/auth/AuthService.java`, `demo-app/src/main/java/com/enterprise/demoapp/api/UserApi.java`, `automation/src/test/java/com/enterprise/automation/tests/api/UserApiTest.java`, `automation/src/test/java/com/enterprise/automation/tests/api/AuthServiceExpiryTest.java`.
- **Pre-change baseline:** `mvnw.cmd -B -ntp test -Dsuite=api -Dmaven.compiler.failOnWarning=true` passed with **57 tests, 0 failures, 0 errors, 0 skipped; Checkstyle 0 violations; exit 0**.
- **Post-change local validation:** the same command passed with **59 tests, 0 failures, 0 errors, 0 skipped; Checkstyle 0 violations; exit 0**. Both modules compiled with Java release 17 and compiler warnings treated as errors. `git diff --check` passed; the application and H2 ports were closed after the suite.
- **Self-review:** the lifecycle lock protects the required session operations, and the tests use only synthetic test credentials. An HTTP request that passed authentication before deletion may finish in flight; all authentication checks after deletion completes reject the deleted account's tokens.
- **Implementation commit / push:** `ed2fb00507caaeaed9b4428f15bdc509aef34b76` (`fix(auth): revoke sessions when users are deleted`), pushed to `origin/codex/batch-c-correctness`.
- **Draft PR:** [#17](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/17), targeting `main`.
- **GitHub Actions:** [run 39421373220](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39421373220) passed all jobs on the implementation commit: quality gate (including the Allure failure-redaction verifier), dependency review, Chrome smoke and Allure report.
- **Next:** proceed to C04 atomic stock reservation on the same Batch C branch; keep PR #17 draft and stop after C05 for owner review.
