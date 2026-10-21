# Codex Remediation Master Plan

## Scope and control rules

This plan tracks the owner-authorized remediation roadmap. The audit commit `9a41506` is a reference point; the current source, fetched `origin/main`, tests and workflow results are authoritative. Do not rebuild capabilities that are already implemented and verified. Work in one batch branch at a time, with a draft pull request and an owner review gate before the next batch starts.

Keep the two Maven modules and existing test architecture. Every confirmed defect gets a regression test where mechanically testable. Do not weaken checks, hide failures, expose secrets, or touch unrelated repositories. Local success alone does not complete a chunk when GitHub Actions verification is required.

## Current project status - 2026-10-21

C00-C11 are complete; PR #19 is merged. H01-H06 are complete; PR #21 merged at `2026-10-21T06:09:10Z` as main SHA `23e7878bb4b7958a4124234fe6319c03397e03e8`. Post-merge Main [39553677331](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39553677331), CodeQL [39553677330](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39553677330), and Nightly [39553795238](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39553795238) all passed on that SHA. The two prior CodeQL alerts (#1 `java/sensitive-log`, #2 `java/relative-path-command`) are fixed. That CodeQL scan also opened [alert #3](https://github.com/niranjankagri/enterprise-test-automation-framework/security/code-scanning/3) (`java/sensitive-log`) at `ElementActions.java:51`; it remains untriaged and is recorded in the post-merge evidence. Fresh browser/Grid and JaCoCo results are recorded in `docs/RELEASE_READINESS.md` and `docs/TEST_COVERAGE_MATRIX.md`.

## Batch dependencies and review gates

| Batch | Chunks | Depends on | Branch | Gate |
|---|---|---|---|---|
| A — baseline | C00 | Fresh `origin/main` | `codex/batch-a-baseline` | Draft PR, required CI terminal and green, owner review/merge approval |
| B — security | C01–C02 | Batch A merged by owner | `codex/batch-b-security` | Same |
| C — correctness | C03–C05 | Batch B merged by owner | `codex/batch-c-correctness` | Same |
| D — validation/docs | C06–C08 | Batch C merged by owner | `codex/batch-d-validation-docs` | Same |
| E — quality/release | C09–C11 | Batch D merged by owner | `codex/batch-e-quality-release` | Owner review before merge; PR #19 is now merged |

Do not build a later batch on unmerged work unless the owner explicitly authorizes that dependency change. Within a batch, commit and push each independently verified chunk. A failed or unavailable required check leaves that chunk `NOT VERIFIED` and blocks dependent work.

## Chunk scope and acceptance criteria

| Chunk | Scope | Acceptance criteria |
|---|---|---|
| C00 — repository baseline and coding harness | Inspect branch, HEAD, remotes, worktree, instructions, modules, Java/Maven/Docker/Git/GitHub tooling and workflow triggers. Add a pinned Maven Wrapper when absent, verify distribution provenance, create persistent project records and establish the runnable/blocked baseline. | Wrapper is reproducible and checksum-pinned; Windows and POSIX wrapper entry points are exercised; README and CI use the verified entry point where tested; plan/progress/decision records exist; baseline evidence and blockers are recorded. |
| C01 — Docker network security | Inspect Compose bindings and deployment assumptions; remove unnecessary H2 host publication and bind required host ports to loopback where appropriate. | Compose validates; internal `demo-app:9093` and Grid connections work; exposed host ports match the intended loopback policy; networking docs agree. |
| C02 — Allure report URL redaction | Trace API/UI URL evidence and all relevant `SecretMasker` sinks, including exception paths. | Synthetic URL userinfo and sensitive query values are absent from test-level and step-level failure details, attachments and retained artifacts; the finalized Allure result is inspected after a real synthetic TestNG failure; useful safe URL details remain; regression tests cover API and UI paths. |
| C03 — session revocation on user deletion | Inspect token storage/authentication and deletion behavior, including races. | Deletion revokes active tokens; the token works before deletion and fails after; deleted users cannot log in; expiry and concurrent behavior remain correct and tested. |
| C04 — atomic order stock reservation | Inspect H2 transactions and order creation. | Concurrent orders cannot oversell or make stock negative; affected-row checks and rollback preserve order/stock consistency; deterministic contention regression test passes. |
| C05 — pure unit-suite isolation | Inspect `unit.xml`, `DemoAppLifecycle` and local configuration. | Unit suite requires no app startup, ports or live service; service-dependent tests remain in appropriate suites; the CI quality gate remains valid. |
| C06 — order quantity range validation | Inspect numeric parsing/conversion and API error conventions. | Zero, negative, fractional, malformed and overflow values return client errors without state changes; integer boundaries are covered. |
| C07 — strict boolean configuration | Inspect `ConfigLoader`, demo-app startup options and configuration docs. | Explicit true/false values remain supported with documented normalization; typos and invalid values fail clearly; missing values preserve documented defaults; diagnostics do not repeat invalid input. |
| C08 — documentation consistency | Compare README, relevant docs, POMs and workflows. | Selenium version, wrapper use, environment commands and Docker/network guidance match verified code; only confirmed stale claims are corrected. |
| C09 — regression and coverage reliability | Review changed-path coverage, suite inclusion, parallelism and test-data isolation. | Required regression tests execute in stable suites; high-value gaps are added only when evidence shows they are missing; untested behavior is reported. |
| C10 — CI/CD and security validation | Review workflow triggers, permissions, dependency strategy, artifacts, secrets and scanning. | Required build/test/Docker/report jobs execute; least privilege and artifact handling are justified; no required checks are bypassed. |
| C11 — final review and release readiness | Independently compare cumulative changes to all eight audit findings and M1–M8 capabilities. | Each finding is fixed or clearly unresolved; feasible full checks and CI evidence are recorded; docs and backlog are accurate; PR and review status are recorded accurately. |

## Current Batch E checkpoint

- **C09 - VERIFIED COMPLETE:** coverage inventory expanded and high-risk API authorization, order concurrency, UI/cross-layer, framework and evidence gaps were addressed with named tests in `docs/TEST_COVERAGE_MATRIX.md`.
- **C10 - VERIFIED COMPLETE:** PR CI [39477093094](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093094), CodeQL [39477093115](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093115), and nightly [39477175118](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477175118) passed on implementation source `6b3fb1971278940df3b4f8882b470b47e4bd3452`. JaCoCo verification now requires executed lines and branches per module and in `OrderApi` / `ApiClient`.
- **C11 - VERIFIED COMPLETE; PR #19 MERGED:** local full Chrome, serial integration, retained Allure verification, browser/Grid matrix, container coverage, cleanup and source/dependency checks passed. PR #19 merged on 2026-10-21; post-merge Main, CodeQL and Nightly passed on `95c34d61e9421c237d3215f6eb123437bda29248`. See the post-merge evidence in `docs/CODEX_PROGRESS.md` and `docs/RELEASE_READINESS.md`.

## Original M1–M8 capability mapping

The project memory records the original implementation sequence as M1–M10. This table preserves the original first eight milestone boundaries; C00–C11 are remediation chunks, not replacement milestone numbers. Verify capabilities against current source and tests at C11.

| Milestone | Existing capability to recheck | Related remediation chunks |
|---|---|---|
| M1 | Multi-module Maven foundation, Java 17 target, framework package structure and build identity | C00, C08, C09, C11 |
| M2 | Layered configuration, browser options/factory, thread-local driver management | C07, C08, C09, C11 |
| M3 | ShopEase demo app, Selenium page/component model and UI smoke path | C01, C02, C05, C08, C09, C11 |
| M4 | Test data objects/providers, UI regression journeys and cleanup | C04, C05, C09, C11 |
| M5 | REST Assured client/services, authentication, user/product/order APIs | C02, C03, C04, C06, C09, C11 |
| M6 | H2/JDBC layer, database assertions and cross-layer integration | C01, C04, C09, C11 |
| M7 | TestNG suites, parallel execution, retries and execution metadata | C05, C09, C10, C11 |
| M8 | Allure reporting, evidence capture, logging and secret masking | C02, C09, C10, C11 |

Docker/Grid, Actions, Checkstyle and project documentation were added in the original M9–M10 work and are covered by C01, C08, C10 and C11 as cross-cutting capabilities.

## C00 baseline record

- Audit baseline: `9a415067fcce2bd4e658f37029775853936ab4da`.
- After `git fetch --prune origin`, `origin/main` was still the same SHA; worktree was clean. Active branch: `codex/batch-a-baseline`.
- Maven Wrapper was absent. Java 27, IntelliJ-bundled Maven 3.9.16, Git, Docker CLI and GitHub CLI are available. Docker daemon is unavailable; `gh auth status` reports the saved token is invalid.
- The CI unit baseline passed using the current Maven installation: 32 passed, 0 failed, 0 errors, 0 skipped; Checkstyle had 0 violations. The unit suite currently starts the demo app because `unit.xml` includes `DemoAppLifecycle` and `local.properties` sets `app.autostart=true`; this is a verified C05 issue, not changed in C00.
- Wrapper provenance and execution evidence are in `docs/CODEX_PROGRESS.md` and the engineering decisions in `docs/CODEX_DECISIONS.md`.

## Batch D-to-E execution checkpoint (historical)

- PR #17 (Batch C) was merged by the owner; Batch D started from `origin/main` at `91b62f9f501b49f22ca1411e21deb7f9ab0670d4`. After Dependabot PR #14 advanced main to `9ffe710242c04200e219626b3497c69ee4c98e75`, the feature branch merged that main commit as `324c9a1baab28d404b926909c473038aa753694a`; PR run `39441537561` passed all applicable jobs.
- C06 is implemented and verified on `c54b77a816954c22de21a0ee3bf9e58966f87b9c`: overflow quantities are rejected without state changes, the API suite passed 74/74 locally, and PR Actions run `39436856557` passed all jobs.
- C07 is implemented and verified on `6ce37369fa4812409341f2bd34825830b3de4023`: framework booleans and `DB_ALLOW_REMOTE` reject invalid text; the unit suite passed 41/41 locally and PR Actions run `39439648609` passed all jobs.
- C08 documentation consistency was verified complete on `5284f992c369087f53fc510353a4d30ce4ebd4bc`; local source comparison passed and PR Actions run `39443489388` passed every applicable job. PR #18 subsequently merged at `22559e5ed2e908664bc45a02dd34f1c6644fc7a7` on 2026-10-20. The former stop-before-Batch-E instruction was the prior owner review gate and is superseded by the current authorization.
- The traceable coverage snapshot is `docs/TEST_COVERAGE_MATRIX.md`. It separates requirement/route coverage, executed suites, Java line/branch coverage, browser evidence and known gaps; no coverage percentages are reported without a defined denominator.

## Batch E implementation checkpoint (pre-merge history)

- Batch E branch: `codex/batch-e-quality-release`, created from freshly fetched `origin/main` at `22559e5ed2e908664bc45a02dd34f1c6644fc7a7`; PR #18 is confirmed merged. The starting worktree was clean.
- Pre-change baseline on the starting SHA: unit 41/41, API 74/74, integration/DB/UI cross-layer 23/23; all reported 0 failures, errors and skips, with Checkstyle 0. The persisted Allure failure-redaction verifier passed. Docker Engine 29.8.2 is running and Compose configuration validates.
- GitHub repository metadata reports secret scanning and push protection enabled, Dependabot security updates disabled, and CodeQL default setup not configured. These are read-only observations; repository settings have not been changed.
- PR #19 is the single draft owner-review PR for Batch E. C09 implementation commits are `7a7b69b`, `cae2f0e` and `4e2f787`; the access matrix, order concurrency, UI evidence and direct cancellation/H2 assertions are covered by current tests. C09 local validations are recorded in `docs/CODEX_PROGRESS.md` and mapped row-by-row in `docs/TEST_COVERAGE_MATRIX.md`.
- C09 and C10 are verified complete; their original evidence is preserved above and in `docs/CODEX_PROGRESS.md`.
- Latest implementation tested: `6b3fb1971278940df3b4f8882b470b47e4bd3452`. PR CI [39477093094](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093094), CodeQL [39477093115](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477093115), and nightly [39477175118](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39477175118) passed.
- Documentation PR head `e442e9922a3e3ab0c7a119b4cef15f25034d8897` passed PR CI [39480246928](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39480246928) and CodeQL [39480246914](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39480246914).
- Historical Batch E review gate: PR #19 was kept open until its required checks passed and was then merged by the owner on 2026-10-21. This supersedes the pre-merge checkpoint above.

## Post-merge verification - 2026-10-21

- PR #19 merged at `2026-10-21T02:05:41Z`; fresh `origin/main` is `95c34d61e9421c237d3215f6eb123437bda29248`.
- Main workflow [39531082307](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082307), CodeQL [39531082296](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531082296) and dispatched main Nightly [39531660067](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39531660067) all completed successfully on that SHA.
- Nightly passed Chrome 213/213, Firefox 157/157, Edge 157/157 and Grid Firefox 157/157; Docker teardown and external JaCoCo verification passed. Current review commands target `main`, not the former Batch E branch.
## Follow-on hardening work: H01-H06

C00-C11 are complete and PR #20 is merged. The separately tracked follow-on hardening plan is [CODEX_HARDENING_ROADMAP.md](CODEX_HARDENING_ROADMAP.md) on branch `codex/coverage-hardening`. H01 implementation SHA `3a322a2a4bc1264b9a81f37d9c3ada48495063d1` passed local Chrome smoke 27/27 with Checkstyle 0; PR CI 39539069715 and CodeQL 39539069697 passed on draft PR #21 head 3bf712df6a006ddd1fa3329e9f9faec2e47ab147. H02 implementation `bac7e20e1245d3fdb0b97fc7c2d0861648480799` passed API 85/85 and integration 37/37 locally with Checkstyle 0; draft PR CI 39540811264 and CodeQL 39540811258 passed on PR head `c3f33954e4df9e4b5c58d80a3b14ae0e348ed35c`. H03 is verified on draft PR #21 head `44b9fa7565ad12ab3d39f53c35bce90801ab6401`: local UI 66/66 and smoke 44/44; PR CI 39543851318 and CodeQL 39543851259 passed. H04 is verified on PR head `ad1e20fbc484c02e28c3d2a41ba59efb98f1c1f3`; local unit 45/45, targeted ConfigLoader mutation 28/28 killed, CI 39545017249, CodeQL 39545017051, and same-run coverage verification passed. This paragraph preserves the checkpoint after H04; the final H01-H06 status is recorded below. Historical C00-C11 records remain intact.

## H01-H06 final verification checkpoint - 2026-10-21

Historical pre-merge checkpoint: PR #20 merged at `6edfa00d7114afb6e3ef347ab3673a7413865006`; H01-H06 were verified on implementation source `1cc3004fb0968bda9fdb5d393f2d1dd1dbb96705`. PR CI [39549978335](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39549978335), CodeQL [39549978164](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39549978164), and Nightly [39550266187](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39550266187) passed at handoff. Documentation checkpoint `652fa0e9d3d5f8121bc889f5aacf02dd8039d605` passed PR CI [39551427397](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39551427397) and CodeQL [39551427589](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39551427589). PR #21 later merged; the current source and post-merge verification are recorded in the project status above and in `docs/CODEX_PROGRESS.md`, `docs/CODEX_HARDENING_ROADMAP.md`, `docs/TEST_COVERAGE_MATRIX.md` and `docs/RELEASE_READINESS.md`.

## Focused follow-up: CodeQL alert #3

- Inspect the reported source-to-sink trace for `java/sensitive-log` at `LoginPage.java:66` to `ElementActions.java:51`.
- Verify the typed password is separate from the logged locator; add regressions proving typed values stay out of log events and Allure step/failure details.
- Document a false-positive trace before proposing dismissal, or fix a confirmed leak. Do not suppress the query or dismiss the alert during the PR.
- Deliver the triage in a separate focused PR and leave it open for owner review.

Local verification checkpoint: commit `71498df9ef4be011178b312a00b0ef078f6bf337` passed the 46-test unit suite, 47-test Chrome smoke suite, and persisted Allure failure-redaction verifier. Draft PR [#23](https://github.com/niranjankagri/enterprise-test-automation-framework/pull/23) head `38c7645565002ab1c5e47efcf72883bd5f06cd76` passed PR CI [39558201952](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39558201952) and CodeQL [39558201940](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39558201940), including quality, dependency review, API, database/order integration, Chrome smoke, coverage merge and Allure report jobs. Alert #3 remains open for owner review.


## Final correctness hardening — 2026-10-21

Owner-authorized single PR on `codex/final-correctness-hardening`, based on main
`1df5ec4b5e308aab43239dba94c8eead5b0c98fb`. This extends completed C/H history.

Implemented: range-safe positive order IDs; 1 MiB request and 1000-item limits;
explicit-null customer PATCH (including preservation of null map entries in the
framework serializer); database-enforced case-insensitive customer email uniqueness;
SQLState conflict translation; row-locked read-modify-write customer/product updates;
role-change session revocation coordinated with login; abandoned-session expiry and
100-session-per-account bound; expiry/endpoint/credential-scoped client token caching
with explicit lifecycle invalidation; configurable bounded API transport; SQL parameter
and driver-error redaction with masked stack diagnostics; OpenAPI numeric limits;
pinned Selenium release, Docker base digests and Allure CLI, plus Docker Dependabot.

Local checks: unit 51/51 and API 93/93 passed with Checkstyle 0 and compiler warnings
as errors (Java 17). The read-timeout regression uses a gated loopback handler.
Browser validation is delegated to PR CI and the `full-validation` label's existing
Nightly workflow. Local smoke was blocked by automatic approval review because
Selenium Manager attempted external telemetry. Supported `SE_AVOID_STATS=true` is
now set for CI/container browser execution; no telemetry authorization is assumed.
Docker is not installed locally, so container execution must be verified remotely.
Remote check status and final evidence are recorded in the PR before handoff.
