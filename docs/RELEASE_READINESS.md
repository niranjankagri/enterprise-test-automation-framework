# Release Readiness - Batch E

## Follow-on H01-H06 hardening status

C00-C11 are complete; PR #20 was merged by the owner. Follow-on work is tracked separately in [CODEX_HARDENING_ROADMAP.md](CODEX_HARDENING_ROADMAP.md), on branch `codex/coverage-hardening`, based on `origin/main` SHA `6edfa00d7114afb6e3ef347ab3673a7413865006`. H01 implementation SHA `3a322a2a4bc1264b9a81f37d9c3ada48495063d1` passed local Chrome smoke 27/27 with Checkstyle 0; draft PR #21 CI 39539069715 and CodeQL 39539069697 passed on head 3bf712df6a006ddd1fa3329e9f9faec2e47ab147. H02 implementation SHA `bac7e20e1245d3fdb0b97fc7c2d0861648480799` passed API 85/85 and integration 37/37 locally, zero failures/errors/skips and Checkstyle 0. Draft PR head `c3f33954e4df9e4b5c58d80a3b14ae0e348ed35c` passed all jobs in CI [39540811264](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811264), including integration 37/37, smoke 27/27 and coverage verification; CodeQL [39540811258](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811258) passed. H01-H02 are verified; H03-H06 remain pending. This is an interim status, not the final H06 handoff.

## Current status

**C09, C10 and C11 are verified complete, and PR #19 merged on 2026-10-21 at `2026-10-21T02:05:41Z`.** The merge commit is current `main` SHA `95c34d61e9421c237d3215f6eb123437bda29248` (merged PR head `7d7eb13a972f6f7f41d8ff1ce56491fc8558b3bf`). Post-merge Main [39531082307](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082307), CodeQL [39531082296](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082296) and main Nightly [39531660067](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531660067) completed successfully on that SHA. The Nightly passed Chrome 213/213, Firefox 157/157, Edge 157/157 and Grid Firefox 157/157, with reporting, Docker teardown and coverage verification green. No deployment or release was performed.

- Baseline: `22559e5ed2e908664bc45a02dd34f1c6644fc7a7` (PR #18 merge to `main`).
- Final tested main revision: `95c34d61e9421c237d3215f6eb123437bda29248` (PR #19 merge commit; Main, CodeQL and Nightly verified).
- Verified documentation PR head: `e442e9922a3e3ab0c7a119b4cef15f25034d8897`; all required PR CI jobs and CodeQL passed on that head.
- Final PR CI tested merge SHA: `20cd87bd4286347215a5491bbe90603e47ae2d4d` for the implementation-source coverage merge.
- Pull request: [#19 - Batch E quality and release readiness](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/19), merged.

## C09, C10, C11 and M1-M8 status

| Scope | Status | Evidence |
|---|---|---|
| C09 - regression and coverage reliability | VERIFIED | The route/method/principal inventory, business rules, UI journeys, framework behavior and security evidence are mapped in `TEST_COVERAGE_MATRIX.md`; high-risk gaps received targeted regressions. |
| C10 - CI/CD, security validation and Java coverage | VERIFIED | PR quality, dependency review, API, integration/order, Chrome smoke, coverage merge, report and CodeQL jobs passed; JaCoCo reports contain production classes and same-revision execution data. |
| C11 - final review and release readiness | VERIFIED / MERGED | Final full Chrome, serial integration, persisted Allure verifier, main nightly browser/Grid, coverage and cleanup checks passed after PR #19 merged. |
| M1 - Maven foundation / Java build identity | VERIFIED | Two-module Java 17-target build compiled with warnings-as-errors; Maven Wrapper used for final local suites. |
| M2 - configuration and browser/driver foundation | VERIFIED | Configuration tests and deterministic browser setup/cleanup failure regression passed; Chrome, Firefox, Edge and Grid ran. |
| M3 - app, page/component model and UI journeys | VERIFIED | Existing ShopEase and page/component architecture retained; full Chrome and nightly browser/Grid regressions passed. |
| M4 - test data, UI journeys and cleanup | VERIFIED | Test data, cleanup, UI/API/DB journeys and report-safe login parameter tests passed; no app/H2 listeners remained after local runs. |
| M5 - REST Assured APIs, auth and business rules | VERIFIED | 26 route/method declarations, protected access matrix, auth revocation, validation and order rules have explicit evidence. |
| M6 - H2/JDBC and cross-layer integration | VERIFIED | Integration suite passed in parallel and serial modes; stock contention, transaction rollback and UI/API/DB journeys passed. |
| M7 - TestNG suites, parallel execution and retries | VERIFIED | Unit/API/full/serial suites passed; TestNG reported zero skipped; the deliberate Allure failure fixture is isolated and verified separately. |
| M8 - Allure, logs and sensitive evidence | VERIFIED | Final persisted synthetic failure kept its original exception and redacted test/step details. Screenshot masking uses temporary overlays without replacing DOM nodes and omits evidence if masking, capture or restoration cannot be verified. Pixel masking remains limited to visible password and explicitly marked content. |

### Original audit findings C01-C08

| Finding | Status | Current evidence |
|---|---|---|
| C01 - Docker network security | VERIFIED | Compose binds app/Grid host ports to loopback, does not publish H2, and nightly Docker/Grid app-to-H2 and Grid checks passed with cleanup. |
| C02 - Allure URL redaction | VERIFIED | API/UI URL evidence and final test/step failure details are sanitized; the isolated synthetic failure verifier passed locally and in PR CI. |
| C03 - Session revocation on deletion | VERIFIED | User deletion revokes all active sessions, deleted users cannot log in, exact expiry and login/deletion races have regressions. |
| C04 - Atomic stock reservation | VERIFIED | Conditional reservations, affected-row checks, transaction rollback, deterministic contention and order lifecycle races passed integration regressions. |
| C05 - Pure unit-suite isolation | VERIFIED | Unit suite excludes application lifecycle; `UnitSuiteIsolationTest` passes, and local unit execution requires no app/H2 listener. |
| C06 - Quantity range validation | VERIFIED | Invalid shapes and overflow are rejected; mixed-line rollback and unchanged stock/order state are asserted. |
| C07 - Strict boolean configuration | VERIFIED | Valid normalization/defaults/precedence and invalid key-specific diagnostics are tested without echoing secret values. |
| C08 - Documentation consistency | VERIFIED | README, suite commands, Java/dependency versions, Maven Wrapper and Docker/network guidance were checked against source/configuration. |

Docker, Selenium Grid and GitHub Actions are verified cross-cutting capabilities; see the run evidence below.

## Commands and validation results

All commands below ran on the final implementation source SHA unless noted. Maven commands used the repository wrapper, Java 27 runtime and Java 17 target. Every Maven suite reported Checkstyle with zero violations.

| Execution | Command / source | Result |
|---|---|---|
| Unit | `.\mvnw.cmd -B -ntp clean test '-Dsuite=unit' '-Dmaven.compiler.failOnWarning=true'` | 43 passed, 0 failures, 0 errors, 0 skipped; local. |
| API | PR job `API regressions` | 78 passed, 0 failures, 0 errors, 0 skipped; final PR CI. |
| Integration and DB | PR job `Database/order integration`; local serial command below | 28 passed, 0 failures, 0 errors, 0 skipped in each execution. Parallel integration passed in PR CI; serial local run passed with one thread. |
| UI | `.\mvnw.cmd -B -ntp clean test '-Dsuite=ui' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'` | 59 passed, 0 failures, 0 errors, 0 skipped; local. |
| Full Chrome | `.\mvnw.cmd -B -ntp clean test '-Dsuite=full' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'` | 213 passed, 0 failures, 0 errors, 0 skipped; local class-parallel run. |
| Local Chrome smoke | `.\mvnw.cmd -B -ntp clean test '-Dsuite=smoke' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'` | Exit 0; 23 passed, 0 failures, 0 errors, 0 skipped; includes reversible screenshot masking regression. |
| PR Chrome smoke | PR job `Smoke / smoke-chrome` | 23 passed, 0 failures, 0 errors, 0 skipped; screenshot overlay regression included. |
| Main Nightly browser results | Run [39531660067](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531660067) on `95c34d61e9421c237d3215f6eb123437bda29248` | Full Chrome 213/213; Firefox 157/157; Edge 157/157; Selenium Grid Firefox 157/157. All jobs and Allure reporting passed; each suite reported 0 failures, errors and skips. Docker cleanup and external coverage verification passed. |
| Allure failure verifier | `python scripts/verify_allure_failure_redaction.py` and PR quality job | Passed. TestNG retained the synthetic `IllegalStateException`; finalized result was broken and test-level/step-level details were redacted. |
| JaCoCo verifier regressions | `python -B -m unittest discover -s scripts/tests -v` | Exit 0; 4 tests passed, covering executed module/class counters and zero-execution rejection. |
| Compose configuration | `docker compose -f docker/docker-compose.yml config --quiet` | Passed locally. Nightly Docker/Grid job started the platform, validated app/Grid tests and external coverage, then stopped containers successfully. |
| Post-run cleanup | `Get-NetTCPConnection -State Listen -LocalPort 8080,9092`; `docker compose -f docker/docker-compose.yml ps --all` | No app/H2 listeners and no running Compose services after final full Chrome run. |
| Diff hygiene | `git diff --check` | Passed before final documentation commit; final docs-only diff is checked before push. |

The final local full-run TestNG result records 213 passed, 0 failed and 0 skipped. Surefire has 213 testcase elements representing 210 distinct method names because parameterized invocations share method names; its flake count is 0. The dedicated Allure failure fixture is excluded from `full.xml` and verified separately. The full Maven/TestNG summary remains 213 passed.

## GitHub Actions evidence

- PR CI [run 39477093094](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093094) - all seven jobs passed: Dependency Review; quality/unit plus persisted Allure verifier; database/order integration; Chrome smoke; API regressions; merged Java coverage reports; Allure report.
- CodeQL [run 39477093115](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093115) - Analyze Java passed.
- Nightly [run 39477175118](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477175118) - full Chrome, Firefox/Edge regressions, Docker/Selenium Grid, combined Allure report and cleanup all passed on the Batch E source SHA.
- Post-merge Main [run 39531082307](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082307) - build/package + unit 43/43, API, Chrome UI, Edge smoke and Allure report all passed on main merge SHA `95c34d61e9421c237d3215f6eb123437bda29248`.
- Post-merge CodeQL [run 39531082296](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082296) - Analyze Java passed on the same main merge SHA.
- Main Nightly [run 39531660067](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531660067) - Chrome 213/213, Firefox 157/157, Edge 157/157 and Grid Firefox 157/157 passed; Allure, Compose teardown and external coverage validation passed.
- Documentation-head PR CI [run 39480246928](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39480246928) - all seven jobs passed on `e442e9922a3e3ab0c7a119b4cef15f25034d8897` (unit 43/43, API 78/78, integration 28/28, smoke 23/23); JaCoCo verifier/report and Allure report passed. CodeQL [run 39480246914](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39480246914) passed on the same head.

The earlier PR and implementation-source runs above are retained as historical evidence. Post-merge Main, CodeQL and the dispatched Nightly all checked out the merged `main` SHA `95c34d61e9421c237d3215f6eb123437bda29248`.

## Java line and branch coverage

JaCoCo 0.8.15 reports production classes only. These are executed Java code metrics, not requirement, endpoint or browser coverage. No threshold is imposed. The PR test-JVM reports and separate nightly Grid/container capture are not combined across revisions or processes.

| Source and execution | Module | Production classes | Lines covered / total | Branches covered / total |
|---|---|---:|---:|---:|
| PR merge SHA `20cd87bd4286347215a5491bbe90603e47ae2d4d`; four non-empty same-SHA inputs | `demo-app` | 24 | 912 / 975 (93.5%) | 262 / 334 (78.4%) |
| PR merge SHA `20cd87bd4286347215a5491bbe90603e47ae2d4d`; four non-empty same-SHA inputs | `automation` | 100 | 1207 / 1473 (81.9%) | 233 / 384 (60.7%) |
| Nightly Grid source SHA `6b3fb1971278940df3b4f8882b470b47e4bd3452`; separate process capture | `demo-app` | 24 | 912 / 975 (93.5%) | 266 / 334 (79.6%) |
| Nightly Grid source SHA `6b3fb1971278940df3b4f8882b470b47e4bd3452`; separate process capture | `automation` | 100 | 1173 / 1473 (79.6%) | 219 / 384 (57.0%) |
| Main Nightly Grid source SHA `95c34d61e9421c237d3215f6eb123437bda29248`; run 39531660067 | `demo-app` | 24 | 912 / 975 (93.5%) | 266 / 334 (79.6%) |
| Main Nightly Grid source SHA `95c34d61e9421c237d3215f6eb123437bda29248`; run 39531660067 | `automation` | 100 | 1173 / 1473 (79.6%) | 219 / 384 (57.0%) |

The CI verifier rejects zero covered line/branch counts per module and requires positive covered line and branch counts in designated `OrderApi` (`demo-app`) and `ApiClient` (`automation`) classes. Its Python regressions reject empty execution and aggregate-only coverage. In-process `demo-app` execution is captured by the automation agent; Docker/Grid app-container coverage is collected separately. These reports describe the execution paths above and should not be presented as repository-wide functional coverage percentages.

## Requirement, authorization and browser coverage gaps

- The API table enumerates the 26 implemented method/route declarations: public login plus 25 protected route/method pairs. `AuthorizationRouteMatrixTest` exercises each protected pair as anonymous, VIEWER and ADMIN with valid request fixtures, including `/api/stats` and user GET-by-ID. Denied writes compare persisted state. No protected route/method cell remains unmapped.
- User list/detail credential omission, multiple-session deletion revocation, malformed/unknown/expired/logout tokens, exact expiry, role promotion, deletion/login race, order validation/rollback, exact-stock contention, duplicate lines, cancellation races and API/UI/database consistency have named tests in the matrix.
- Requirement coverage is not expressed as a percentage because this repository has no complete independent product-requirements denominator. Browser execution is reported as suite evidence, not as a percentage of all possible browser behavior.
- Remaining test limitations: unsupported media-type behavior has no defined API contract; direct exhaustive foreign-key violation cases are not covered; UI loading/network-error states are not a separately modeled contract; manual screen-reader/assistive-technology review was not performed; screenshot masking applies only to password and explicitly marked values and does not guarantee universal pixel redaction.

## Security findings and remaining blockers

- Post-merge CodeQL [39531082296](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082296) passed on main SHA `95c34d61e9421c237d3215f6eb123437bda29248`; current open CodeQL alerts: 0. Current native secret-scanning alerts: 0. Repository secret scanning and push protection are enabled. Dependabot security updates are disabled at the repository setting; CodeQL default setup is not configured because the advanced workflow runs analysis. No GitHub settings were modified.
- A retained-artifact pattern scan found no checked demo/synthetic credentials in Allure result JSON, Surefire results or runtime logs. Two matches in the broad artifact scan were the static demo seed credential shown in generated JaCoCo `DemoApp.java.html` source listings; that source is already part of the demo repository. No runtime evidence contained those values.
- There are no blocked required checks. The limitations above are scoped test/security follow-ups, not failed release gates. PR #19 is merged; the Batch E owner review gate is complete.

## Owner full-suite review commands

Windows PowerShell, from the latest `main` checkout:

```powershell
git switch main
git pull --ff-only origin main
.\mvnw.cmd -B -ntp clean test '-Dsuite=unit' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp clean test '-Dsuite=api' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp clean test '-Dsuite=integration' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp clean test '-Dsuite=integration' '-Dparallel=none' '-Dthreads=1' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp clean test '-Dsuite=ui' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp clean test '-Dsuite=full' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'
python scripts/verify_allure_failure_redaction.py
docker compose -f docker/docker-compose.yml config --quiet
gh workflow run "Nightly regression" --ref main
```

When dispatched on `main`, the nightly workflow uses the existing Chrome/Firefox/Edge and Docker/Selenium Grid provisioning, produces source-stamped coverage inputs and runs container cleanup. Review the linked main run before treating any local rerun as a replacement for post-merge CI evidence.
