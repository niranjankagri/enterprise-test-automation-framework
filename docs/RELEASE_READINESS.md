# Release Readiness — Batch E

## Current status

**IN PROGRESS — NOT READY FOR RELEASE OR MERGE.** This record is for owner review of C09–C11. The current tested starting implementation is `22559e5ed2e908664bc45a02dd34f1c6644fc7a7` (merged PR #18, 2026-10-20). Final implementation SHA and CI evidence will be recorded after completion.

## Baseline environment and executions

| Check | Command | Result on baseline SHA | Notes |
|---|---|---|---|
| Pure unit suite | `./mvnw -B -ntp clean test -Dsuite=unit -Dmaven.compiler.failOnWarning=true` (Windows: quote `-D` arguments in PowerShell) | 41 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 | Local JDK 27, project bytecode target 17. |
| API suite | `./mvnw -B -ntp test -Dsuite=api -Dmaven.compiler.failOnWarning=true` | 74 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 | App/H2 started and stopped by suite lifecycle. |
| Integration/DB/cross-layer suite | `./mvnw -B -ntp test -Dsuite=integration -Dmaven.compiler.failOnWarning=true` | 23 passed, 0 failed, 0 errors, 0 skipped; Checkstyle 0 | Includes browser-backed API/UI/DB checks and order contention. |
| Persisted Allure failure verifier | `python scripts/verify_allure_failure_redaction.py` | Passed | Final synthetic failing TestNG result inspected; test-level and step-level details were redacted while failure was preserved. |
| Docker Compose configuration | `docker compose -f docker/docker-compose.yml config --quiet` | Passed | Docker Engine 29.8.2 available; runtime deployment checks remain pending final revision. |

The initial unquoted PowerShell invocation of the unit command was rejected by Maven before tests ran; the quoted property invocation above is the successful baseline. No Java coverage instrumentation exists in the starting POMs/workflows.

## C09, C10, C11 and M1–M8 status

| Scope | Status | Evidence |
|---|---|---|
| C09 — coverage inventory and regression reliability | IN PROGRESS | Baseline suites and route declarations inspected; see `TEST_COVERAGE_MATRIX.md`. |
| C10 — CI, security scans and Java coverage | PENDING | CI jobs, repository settings and Compose inspected; no JaCoCo report exists at baseline. |
| C11 — final audit and release readiness | PENDING | Requires final revision validation and final evidence links. |
| M1 — Maven foundation / Java build identity | PENDING FINAL RECHECK | Existing two-module POMs and Java 17 target; baseline Maven compile passed. |
| M2 — configuration and browser/driver foundation | PENDING FINAL RECHECK | Existing configuration, browser factories and platform tests. |
| M3 — application, page/component model and UI journeys | PENDING FINAL RECHECK | Existing demo app and Selenium suites; baseline integration suite passed. |
| M4 — test data, UI journeys and cleanup | PENDING FINAL RECHECK | Existing providers, unique data and cleanup registry; negative/parallel cleanup review in progress. |
| M5 — REST Assured APIs, auth and business rules | PENDING FINAL RECHECK | Baseline API suite passed; route × role matrix and high-risk business gaps in progress. |
| M6 — H2/JDBC and cross-layer integration | PENDING FINAL RECHECK | Baseline integration suite passed; broader race/constraint evidence pending. |
| M7 — TestNG suites, parallel execution and retries | PENDING FINAL RECHECK | Unit isolation exists; lifecycle, parallelism and retry behavior need final audit. |
| M8 — Allure, logs and sensitive evidence | PENDING FINAL RECHECK | Baseline persisted-failure redaction verifier passed; screenshot pixel limitation remains under review. |

## Coverage, security and limitations

- Requirement, authorization, execution, Java line/branch and browser coverage are separate dimensions; no percentages are reported until denominators and instrumentation are verified.
- Repository secret scanning and push protection report enabled through GitHub repository metadata. CodeQL default setup is not configured; workflow scanning decision and execution evidence are pending.
- Browser matrix, Docker/Selenium Grid runtime, final CI, JaCoCo metrics, accessible-page automated checks, source scanning, residual skips and final blockers are pending.

## Owner full-suite review commands

Windows PowerShell:

```powershell
.\mvnw.cmd -B -ntp clean test '-Dsuite=unit' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp test '-Dsuite=api' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp test '-Dsuite=integration' '-Dmaven.compiler.failOnWarning=true'
.\mvnw.cmd -B -ntp test '-Dsuite=full' '-Dparallel=classes' '-Dthreads=4' '-Dmaven.compiler.failOnWarning=true'
docker compose -f docker/docker-compose.yml config --quiet
```

These commands will be revised to include validated coverage and browser/Grid execution paths before the final handoff.
