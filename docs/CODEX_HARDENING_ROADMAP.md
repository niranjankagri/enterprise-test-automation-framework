# H01–H06 Coverage Hardening Roadmap

## Scope and review rules

This roadmap tracks follow-on hardening separately from completed C00–C11 remediation. PR #20 was merged by the owner; the new work starts from freshly fetched `origin/main` at `6edfa00d7114afb6e3ef347ab3673a7413865006` on branch `codex/coverage-hardening`. C00–C11 history remains in the existing project records.

Deliver all H01–H06 milestones through one draft PR with independently reviewable commits. Preserve the two Maven modules and existing Selenium, TestNG, REST Assured, H2, Allure, Docker/Grid and Actions architecture. Do not merge, deploy, publish a release or enable auto-merge.

Statuses: `PENDING`, `IN PROGRESS`, `VERIFIED LOCALLY`, `VERIFIED` (local checks and applicable CI passed), or `BLOCKED / NOT VERIFIED`.

## Milestones

| ID | Objective | Acceptance evidence | Status |
|---|---|---|---|
| H01 | Deterministic UI failure coverage | Controlled request gating/failure; customer/catalog network-error retry; safe order retry with unchanged DB/API state after rejection; controlled expired-session handling. | VERIFIED |
| H02 | Database constraints and transaction boundaries | Direct uniqueness/FK violations, referenced-row deletion behavior, numeric precision/range, and no partial state after failures; existing stock reservation/cancellation concurrency retained. | VERIFIED |
| H03 | Accessibility of critical journeys | Pinned compatible automated accessibility checks for login/customer/checkout, keyboard/focus/dialog assertions, fixes for confirmed defects, and explicit manual-check limits. | VERIFIED |
| H04 | Coverage-driven assertion improvement | Fresh same-revision module JaCoCo reports, branch review by business risk, targeted behavioral tests, and an assessed (not assumed) mutation-testing option. | VERIFIED |
| H05 | Dependency and security maintenance | Current CodeQL/dependency/secret-check evidence, verified Dependabot security-update setting, artifact-retention review, and preserved screenshot limits. | VERIFIED |
| H06 | Final validation and handoff | Relevant suites, browser/Grid, redaction, coverage verification, security checks, cleanup/residue, exact SHAs and final CI on the PR revision. | VERIFIED |

## H01 — Deterministic UI failure coverage

Confirmed defects: customer and product loading allowed rejected API promises to leave the page in a permanent loading state; order failure/retry and browser expiry needed deterministic end-to-end evidence.

Implementation: use a test-only in-browser fetch simulator that can gate a matching request, return a synthetic HTTP status, or throw a synthetic network error. No production outage or timing race is required. Customer/catalog loading shows a retryable alert and restores readiness after request failure. Checkout rejection keeps the cart and re-enables submission.

Regression tests:
- `CustomerManagementTest.customerListFailureShowsRetryAndRecovers`
- `ProductCatalogTest.catalogueFailureShowsRetryAndRecovers`
- `PurchaseJourneyTest.failedOrderSubmissionCanBeRetriedWithoutPartialWrites`
- `LoginRegressionTest.expiredBrowserSessionClearsTheCartAndReturnsToLogin`

Current local evidence on implementation SHA `3a322a2a4bc1264b9a81f37d9c3ada48495063d1`: `.\mvnw.cmd -B -ntp clean test '-Dsuite=smoke' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'` passed 27 tests, zero failures/errors/skips, Checkstyle zero. Draft PR #21 head 3bf712df6a006ddd1fa3329e9f9faec2e47ab147 passed all jobs in PR CI run 39539069715 and CodeQL run 39539069697.

## H02 — Database constraints and transaction boundaries

Confirmed gaps: the database had unique and foreign-key constraints but no direct violation tests. Product validation accepted sub-cent prices that H2 rounded during storage and values above `DECIMAL(10,2)`; an order total above `DECIMAL(12,2)` reached persistence and became a 500.

Implementation commit `bac7e20e1245d3fdb0b97fc7c2d0861648480799` validates product prices as positive, within `99999999.99` and exactly representable to cents (insignificant trailing zeroes are accepted). Order totals above `9999999999.99` return a field-specific 400 before stock reservation or writes. H2 tests directly exercise duplicate username/email/SKU, missing foreign keys, referenced customer/product deletion, order-line cascade and test-data cleanup.

Regression coverage: `DatabaseLayerTest.uniqueConstraintsRejectDuplicateKeys`, `DatabaseLayerTest.foreignKeysProtectReferencesAndCascadeOrderLines`, `ProductApiTest.priceWithMoreThanTwoDecimalPlacesIsRejectedWithoutWriting`, `ProductApiTest.priceAboveDatabaseMaximumIsRejectedWithoutWriting`, `ProductApiTest.maximumSupportedProductPriceIsStoredExactly`, `ProductApiTest.insignificantTrailingPriceZeroesAreAcceptedAndNormalized`, `ProductApiTest.unsupportedPricePrecisionPatchLeavesStoredValueUnchanged`, `OrderApiTest.maximumSupportedOrderTotalIsStoredExactly`, and `OrderApiTest.orderTotalAboveDatabaseMaximumIsRejectedWithoutWriting`.

Reproduction before the implementation: API suite 83 tests, 3 failures (sub-cent price returned 201; oversized price and total returned 500). After implementation, exact-commit API suite passed 85/85 and integration suite passed 37/37; both had zero failures/errors/skips and Checkstyle 0. The integration run retained the existing deterministic stock reservation/cancellation tests. Draft PR #21 head `c3f33954e4df9e4b5c58d80a3b14ae0e348ed35c` passed all PR jobs in [CI run 39540811264](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811264), including API, integration (37/37), Chrome smoke (27/27), unit/quality, dependency review, Allure report and same-revision coverage verification. [CodeQL run 39540811258](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39540811258) passed.

## H03 — Accessibility of critical journeys

Audit the existing login, navigation, customer form and checkout tests before selecting a tool. If an automated scanner is added, pin its version, verify compatibility with the current Selenium dependency tree, and keep it in test scope. Fix only confirmed violations and add focused regressions. Keep keyboard and focus assertions. Report automated findings separately from manual screen-reader testing, which is not claimed unless performed.

**Verified implementation:** commit `12cfbbbbd3cb14a77e43a0a7ba813fbac4f072e4` adds test-scoped axe-core Selenium `4.13.0`; its transitive Selenium and WebDriverManager dependencies are excluded so the framework's pinned Selenium/driver setup stays in use. The axe scans run on login, dashboard/navigation, customer list/add-dialog and populated checkout. They found and drove fixes for the empty checkout table header and the customer dialog's nested banner landmark. Login/customer field errors now have accessible descriptions, alert roles, explicit `aria-invalid="true"` and first-invalid-field focus; shared controls have a visible `:focus-visible` outline. Existing modal keyboard-loop, Escape and focus-return assertions remain in `CustomerManagementTest.cancellingTheFormCreatesNothing`.

Regression tests: `LoginRegressionTest.loginFieldsHaveLabelsAndKeyboardFocus`, `LoginRegressionTest.signInIsRejected`, `NavigationSmokeTest.dashboardNavigationHasNoAxeViolations`, `CustomerManagementTest.customerListAndFormHaveNoAxeViolations`, `CustomerManagementTest.invalidInputIsRejectedPerField`, and `CheckoutTest.checkoutHasNoAxeViolationsAndKeyboardFocusIsVisible`. The existing login and customer validation data-provider cases were added to the PR smoke group so those semantics execute in pre-merge CI.

**Local validation:** the complete UI suite passed **66/66** at source commit `12cfbbbbd3cb14a77e43a0a7ba813fbac4f072e4`; the expanded smoke suite passed **44/44** on the source tree committed as `44b9fa7565ad12ab3d39f53c35bce90801ab6401`, with Checkstyle 0 and no skips. **Remote verification:** PR #21 head `44b9fa7565ad12ab3d39f53c35bce90801ab6401` passed all jobs in [CI run 39543851318](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39543851318): unit 43/43, API 85/85, integration 37/37, Chrome smoke 44/44, coverage merge/verifier, dependency review and Allure report; [CodeQL run 39543851259](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39543851259) passed. Automated axe checks are focused page-state checks, not an accessibility certification; **manual screen-reader checks were not performed**.

## H04 — Coverage-driven assertion improvement

Generate fresh `demo-app` and `automation` JaCoCo reports from matching source revision(s). Review uncovered branches by authentication, order lifecycle, configuration, cleanup and reporting risk. Add tests only where an important behavior is not asserted. Assess targeted mutation testing for compatible pure logic; keep it outside the fast PR path and do not add a percentage threshold without a stable baseline and explicit exclusions.

The same-revision PR coverage artifact at H03 head `44b9fa7565ad12ab3d39f53c35bce90801ab6401` measured `demo-app` 922/983 lines (93.8%) and 271/340 branches (79.7%); `automation` 1244/1489 lines (83.5%) and 243/384 branches (63.3%). A smoke-only local report at H03 implementation SHA `12cfbbbbd3cb14a77e43a0a7ba813fbac4f072e4` was also generated and passed `scripts/verify_jacoco_reports.py`; it is not used as the broader baseline. No percentage thresholds are set.

**Verified H04:** At branch head `ad1e20fbc484c02e28c3d2a41ba59efb98f1c1f3`, the clean unit suite passed 45/45 with Checkstyle 0; the Python JaCoCo verifier regressions passed 4/4. A malformed configured URL reproduced credential and query-value disclosure in the exception message and cause; `ConfigLoader` now reports the key and validation failure without retaining either. `ConfigLoaderTest.invalidUrlDiagnosticDoesNotExposeCredentialsOrQueryValues` guards the full cause chain. The retry-count `-1/0/positive` boundary test killed the two surviving PIT boundary mutants. The opt-in `targeted-mutation-testing` profile is pinned to PIT 1.30.1 and its TestNG plugin 1.0.0; the final run killed all 28 generated mutants. No threshold was introduced.

PR #21 head `ad1e20fbc484c02e28c3d2a41ba59efb98f1c1f3` passed all jobs in [CI run 39545017249](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39545017249) and [CodeQL run 39545017051](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39545017051). The PR CI executed merge source SHA `968ace6172557628ce6626e8ec3fb808b4b63039`, whose parents were `origin/main` `6edfa00d7114afb6e3ef347ab3673a7413865006` and the PR head. Same-run production reports verified by `scripts/verify_jacoco_reports.py` measured demo-app 922/983 lines (93.8%), 271/340 branches (79.7%) and automation 1244/1489 lines (83.5%), 243/384 branches (63.3%). Authentication/order branches were already exercised by the existing API and contention suites; the confirmed missing boundary was retry count zero. These are execution metrics, not thresholds or requirement coverage.

## H05 — Dependency and security maintenance

Inspect workflow results, permissions, triggers, artifact retention, Dependabot configuration and repository settings. Verify CodeQL, dependency review and available secret scanning. Dependabot security updates were enabled after preflight confirmed repository admin access; inspect the open CodeQL warnings and add focused fixes if they expose real data or execution risks. Do not change secrets, branch protections or history. Preserve the documented limits of screenshot pixel masking and retained evidence.

## H06 — Final validation and handoff

Run relevant unit, API, integration, full Chrome, Firefox/Edge and Docker/Grid executions; persisted Allure redaction and JaCoCo verifier checks; available security checks; Compose binding and cleanup checks. Inspect reports for failures, skips, retries, warnings, secrets and test residue. Verify applicable CI against the final implementation revision. Update the release-readiness record with exact source SHA, commands/results, CI URLs, app/framework line and branch coverage, browser/Grid outcomes, limitations and copyable owner review commands. Leave the single PR unmerged and ready for owner review only when its required checks pass; otherwise preserve draft state and list exact blockers.

## H05-H06 final verification - 2026-10-21

**Final implementation source:** `1cc3004fb0968bda9fdb5d393f2d1dd1dbb96705`; PR #21 remains the single review PR on `codex/coverage-hardening`.

H05 retained UI-entered sensitive values out of Allure step names and failure details (`LoginRegressionTest.typedUserValuesAreOmittedFromReportSteps`, `ProductCatalogTest.missingCategoryOptionDoesNotPersistRequestedValue`), resolved absolute Git executable lookup in `ExecutionMetadata`, and bounded raw unit/Grid artifact retention to 30/14 days. Dependabot security updates are enabled; repository secret scanning and push protection are enabled with no open secret or Dependabot alerts. Dependency Review and PR CodeQL passed. Two CodeQL alerts still displayed on the old base `main` SHA during PR review (`java/relative-path-command`, `java/sensitive-log`); the branch fixes passed CodeQL, and the alerts require a post-merge main scan to confirm closure. Non-provider pattern scanning is disabled; it is not a required check and no finding is currently open.

**H06 PR verification:** [PR CI 39549978335](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39549978335) and [CodeQL 39549978164](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39549978164) passed on PR head `1cc3004...`. CI counts: unit 46/46, API 85/85, integration 37/37, Chrome smoke 47/47; all have zero failures/errors/skips. The persisted synthetic Allure failure verifier, Dependency Review, JaCoCo merge/strict verifier and report generation passed. [Nightly 39550266187](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39550266187) also passed on the exact implementation SHA: full Chrome 233/233, Firefox 174/174, Edge 174/174 and Docker/Selenium Grid 174/174; all zero failures/errors/skips. Docker/Grid teardown, external app coverage verification and combined Allure report passed.

The documentation checkpoint `652fa0e9d3d5f8121bc889f5aacf02dd8039d605` passed PR CI [39551427397](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39551427397) and CodeQL [39551427589](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39551427589). Unit 46/46, API 85/85, integration 37/37, Chrome smoke 47/47, coverage merge, Allure report and Dependency Review passed. The subsequent progress-record-only commit is rechecked before review readiness.

**Local checks:** `.\mvnw.cmd -B -ntp clean test '-Dsuite=smoke' '-Dheadless=true' '-Dmaven.compiler.failOnWarning=true'` passed 47/47 with Checkstyle 0; `python scripts/verify_allure_failure_redaction.py` passed with the original synthetic exception preserved and test/step details redacted; `python -B -m unittest discover -s scripts/tests -v` passed 4/4; `docker compose -f docker/docker-compose.yml config --quiet` passed. Final local inspection found no owned app/H2 listeners, Java/Selenium/browser processes or Compose services.

**Coverage:** PR CI same-revision reports (`demo-app` 24 classes, 919/983 lines = 93.5%, 271/340 branches = 79.7%; `automation` 100 classes, 1279/1516 lines = 84.4%, 256/400 branches = 64.0%) and separate Nightly Grid reports (`demo-app` 922/983 = 93.8%, 275/340 = 80.9%; `automation` 1222/1516 = 80.6%, 228/400 = 57.0%) both passed the strict verifier. Captures are from separate processes and are not merged. These are production execution metrics, not requirement, browser or accessibility coverage.

The PR has no remaining required-check blocker. Manual screen-reader assessment, undefined unsupported-media-type behavior, optional non-provider secret-pattern scanning, and the post-merge CodeQL alert rescan remain explicit limitations/follow-ups. Pixel masking remains scoped to visible password fields and explicitly marked content; screenshot capture is omitted if masking, capture or restoration cannot be verified.
