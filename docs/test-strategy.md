# Test strategy

## Goals

1. Catch defects where they are cheapest to find and explain: as low in the stack as possible.
2. Keep every test independent, deterministic and parallel-safe.
3. Make every failure self-explanatory (report evidence, diagnostics).

## Test pyramid in this project

| Level | What it checks | Suite / group | Runs |
|---|---|---|---|
| Unit (framework) | configuration rules, data generation, CSV/JSON readers, retry classification, secret masking, log components | `unit` | PR quality gate and main build |
| Platform | real browsers start with the configured settings, one per thread; screenshots | `platform` | full and regression suites where group selection includes it |
| API | route behavior, response contracts, authentication, authorization, validation and business rules | `api` | PR API regressions, main API, nightly full/regression |
| Database | schema, catalogue rows, parameter binding and persistence assertions | `db` | integration suite and nightly full/regression |
| Integration | API → DB (stored values, transactions, hashing); API → DB → UI and UI → API → DB | `integration` | integration suite and nightly full/regression |
| UI | sign-in, navigation, customer management, catalogue, checkout and roles | `ui` | PR Chrome smoke, main UI, nightly full/regression |
| E2E | business journeys (order placement, cancellation) | `e2e` | nightly full suite |

Group membership and suite selection overlap. This table describes layers and CI execution paths, not the set of requirements proven. The [coverage matrix](TEST_COVERAGE_MATRIX.md) tracks individual requirements, named tests, the last verified source SHA and limitations. Test totals vary with data providers and group selection; no coverage percentage is reported here.

Most behaviour is covered at the API level (fast, precise); the UI covers what only the UI can show (forms, field errors, role-based controls, cart behaviour) and a few journeys. Cross-layer tests prove that the layers agree.

## Purpose groups

| Group | Meaning | When |
|---|---|---|
| `smoke` | "is it up and usable?": login, navigation, catalogue, key API reads | pull requests and main; useful after deployment |
| `sanity` | key happy paths and role checks | available as a dedicated suite; no current workflow selects it |
| `regression` | broader functional regression cases, including selected negative and data-driven cases; not complete functional coverage | nightly Firefox, Edge and Docker/Grid suites; overlapping tests can also be selected by Main UI/full suites |
| `security` | a purpose tag for security-related tests such as password hashing and secret masking; not a standalone suite | selected only when a suite includes the test's layer or another group attached to it |

## Test design techniques

- **Equivalence classes and boundaries**: invalid customer fields (missing, too long, wrong format) from `invalid-customers.csv`; quantity 0 vs 1; stock exactly enough vs one too many.
- **State transitions**: order status flow PLACED → SHIPPED → DELIVERED, PLACED → CANCELLED, and the forbidden transitions.
- **Roles and permissions**: representative writes as the read-only viewer are checked (UI controls hidden, API 403); the full route × method × role matrix remains tracked as a coverage gap.
- **Negative and robustness**: malformed JSON, wrong types, unknown ids, wrong HTTP methods, logged-out and invalid tokens.
- **Data-driven**: the same CSV drives UI and API validation tests, so both layers enforce the same rules.
- **Consistency across layers**: what the UI shows = what the API returns = what the database stores.

## Test data

Every test creates what it needs with unique values and removes it afterwards ([ADR-009](adr/ADR-009-every-test-owns-its-data.md), [ADR-012](adr/ADR-012-clean-ups-are-idempotent-and-never-fail-a-test.md)). Reference data (the seeded catalogue) is read-only and described in `testdata/products.json`. Setup and clean-up of UI tests go through the API.

## Environments

| Environment | Application | Database checks | Typical use |
|---|---|---|---|
| `local` | started in-process by the suite | yes | development |
| `qa` | separately running (jar or Docker) | yes | CI, Docker Grid |
| `staging` | production-like, URLs and secrets from CI | skipped unless `DB_URL` is provided | pre-release |

## Flakiness policy

- No fixed sleeps, no implicit waits: tests wait for real application state.
- Assertions never depend on what other tests do (no "latest 5 orders", catalogue checks ignore test products).
- Only infrastructure failures are retried; a test that fails on an assertion is a result, not noise.
- A test that needs a retry more than occasionally is investigated (retries are logged and visible in the report).

## Entry and exit criteria

- **Pull request**: compiles without warnings, Checkstyle clean, unit tests and the persisted Allure failure-redaction verifier pass, API regressions and Chrome smoke pass, and dependency review finds no new high-severity vulnerable dependency.
- **Main**: build, API, UI and smoke green.
- **Release (nightly before release)**: full suite on Chrome and regression on Firefox and Edge green, Grid run green; failures triaged by report category.

## Out of scope

Performance/load testing, visual regression, accessibility audits and cloud device farms are not part of this framework yet (see "Future enhancements" in the README).
