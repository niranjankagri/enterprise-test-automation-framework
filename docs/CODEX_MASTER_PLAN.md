# Codex Remediation Master Plan

## Scope and control rules

This plan tracks the owner-authorized remediation roadmap. The audit commit `9a41506` is a reference point; the current source, fetched `origin/main`, tests and workflow results are authoritative. Do not rebuild capabilities that are already implemented and verified. Work in one batch branch at a time, with a draft pull request and an owner review gate before the next batch starts.

Keep the two Maven modules and existing test architecture. Every confirmed defect gets a regression test where mechanically testable. Do not weaken checks, hide failures, expose secrets, or touch unrelated repositories. Local success alone does not complete a chunk when GitHub Actions verification is required.

## Batch dependencies and review gates

| Batch | Chunks | Depends on | Branch | Gate |
|---|---|---|---|---|
| A — baseline | C00 | Fresh `origin/main` | `codex/batch-a-baseline` | Draft PR, required CI terminal and green, owner review/merge approval |
| B — security | C01–C02 | Batch A merged by owner | `codex/batch-b-security` | Same |
| C — correctness | C03–C05 | Batch B merged by owner | `codex/batch-c-correctness` | Same |
| D — validation/docs | C06–C08 | Batch C merged by owner | `codex/batch-d-validation-docs` | Same |
| E — quality/release | C09–C11 | Batch D merged by owner | `codex/batch-e-quality-release` | Same; leave PR unmerged |

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
| C11 — final review and release readiness | Independently compare cumulative changes to all eight audit findings and M1–M8 capabilities. | Each finding is fixed or clearly unresolved; feasible full checks and CI evidence are recorded; docs and backlog are accurate; PR remains unmerged for owner review. |

## Current Batch E checkpoint

- **C09 - VERIFIED COMPLETE:** coverage inventory expanded and high-risk API authorization, order concurrency, UI/cross-layer, framework and evidence gaps were addressed with named tests in `docs/TEST_COVERAGE_MATRIX.md`.
- **C10 - VERIFIED COMPLETE:** PR #19 CI, integration/order gate, extended CodeQL workflow and same-revision module-specific JaCoCo reporting passed on branch head `62427b353eb6aba3cc96b3371473c80a0f83b6a5`; tested merge SHA `3cede33dffde81c9459a57ff914248571bf14329`. See `docs/CODEX_PROGRESS.md` and `docs/RELEASE_READINESS.md`.
- **C11 - ACTIVE:** run final-source suites, browser matrix, Docker/Selenium Grid, persisted Allure verification and source/dependency checks; audit M1-M8 and original findings; update final coverage and limitations. Keep PR #19 draft until required checks terminate successfully; never merge or deploy.

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

## Current execution checkpoint

- PR #17 (Batch C) was merged by the owner; Batch D started from `origin/main` at `91b62f9f501b49f22ca1411e21deb7f9ab0670d4`. After Dependabot PR #14 advanced main to `9ffe710242c04200e219626b3497c69ee4c98e75`, the feature branch merged that main commit as `324c9a1baab28d404b926909c473038aa753694a`; PR run `39441537561` passed all applicable jobs.
- C06 is implemented and verified on `c54b77a816954c22de21a0ee3bf9e58966f87b9c`: overflow quantities are rejected without state changes, the API suite passed 74/74 locally, and PR Actions run `39436856557` passed all jobs.
- C07 is implemented and verified on `6ce37369fa4812409341f2bd34825830b3de4023`: framework booleans and `DB_ALLOW_REMOTE` reject invalid text; the unit suite passed 41/41 locally and PR Actions run `39439648609` passed all jobs.
- C08 documentation consistency was verified complete on `5284f992c369087f53fc510353a4d30ce4ebd4bc`; local source comparison passed and PR Actions run `39443489388` passed every applicable job. PR #18 subsequently merged at `22559e5ed2e908664bc45a02dd34f1c6644fc7a7` on 2026-10-20. The former stop-before-Batch-E instruction was the prior owner review gate and is superseded by the current authorization.
- The traceable coverage snapshot is `docs/TEST_COVERAGE_MATRIX.md`. It separates requirement/route coverage, executed suites, Java line/branch coverage, browser evidence and known gaps; no coverage percentages are reported without a defined denominator.

## Batch E execution checkpoint — owner-authorized continuation

- Batch E branch: `codex/batch-e-quality-release`, created from freshly fetched `origin/main` at `22559e5ed2e908664bc45a02dd34f1c6644fc7a7`; PR #18 is confirmed merged. The starting worktree was clean.
- Pre-change baseline on the starting SHA: unit 41/41, API 74/74, integration/DB/UI cross-layer 23/23; all reported 0 failures, errors and skips, with Checkstyle 0. The persisted Allure failure-redaction verifier passed. Docker Engine 29.8.2 is running and Compose configuration validates.
- GitHub repository metadata reports secret scanning and push protection enabled, Dependabot security updates disabled, and CodeQL default setup not configured. These are read-only observations; repository settings have not been changed.
- PR #19 is the single draft owner-review PR for Batch E. C09 implementation commits are `7a7b69b`, `cae2f0e` and `4e2f787`; the access matrix, order concurrency, UI evidence and direct cancellation/H2 assertions are covered by current tests. C09 local validations are recorded in `docs/CODEX_PROGRESS.md` and mapped row-by-row in `docs/TEST_COVERAGE_MATRIX.md`.
- C09 Actions run [39460227330](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39460227330) passed all applicable jobs on `4e2f787`; C09 is verified complete. C10 is active: add pre-merge integration/order regression, CodeQL source analysis and measured JaCoCo reports. C11 remains pending final-source full/browser/Grid validation.
- Keep PR #19 draft and unmerged until every owner-authorized acceptance criterion and applicable CI job is verified.
