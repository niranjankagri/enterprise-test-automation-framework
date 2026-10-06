# Test strategy

## Goals

1. Catch defects where they are cheapest to find and explain: as low in the stack as possible.
2. Keep every test independent, deterministic and parallel-safe.
3. Make every failure self-explanatory (report evidence, diagnostics).

## Test pyramid in this project

| Level | What it checks | Suite / group | Count (approx.) | Runs |
|---|---|---|---|---|
| Unit (framework) | configuration rules, data generation, CSV/JSON readers, retry classification, secret masking, log components | `unit` | 32 | every build (quality gate) |
| Platform | real browsers start with the configured settings, one per thread; screenshots | `platform` | 8 | full suite |
| API | every endpoint and method, status codes, headers, payloads, schemas, authentication, authorization, validation, business rules, correlation id | `api` | 57 | main, nightly |
| Database | schema, catalogue rows, parameter binding, assertions | `db` | 13 | nightly, integration |
| Integration | API → DB (stored values, transactions, hashing); API → DB → UI and UI → API → DB | `integration` | 9 | nightly |
| UI | sign-in, navigation, customer management, catalogue, checkout, roles | `ui` | 53 | main, nightly |
| E2E | business journeys (order placement, cancellation) | `e2e` | 5 | nightly |

Most behaviour is covered at the API level (fast, precise); the UI covers what only the UI can show (forms, field errors, role-based controls, cart behaviour) and a few journeys. Cross-layer tests prove that the layers agree.

## Purpose groups

| Group | Meaning | When |
|---|---|---|
| `smoke` | "is it up and usable?": login, navigation, catalogue, key API reads | every pull request, after deployment |
| `sanity` | key happy paths and role checks | after deployment |
| `regression` | complete functional coverage including negative and data-driven cases | main branch, nightly |
| `security` | password hashing, secret masking | with regression |

## Test design techniques

- **Equivalence classes and boundaries**: invalid customer fields (missing, too long, wrong format) from `invalid-customers.csv`; quantity 0 vs 1; stock exactly enough vs one too many.
- **State transitions**: order status flow PLACED → SHIPPED → DELIVERED, PLACED → CANCELLED, and the forbidden transitions.
- **Roles and permissions**: every write as the read-only viewer must fail (UI controls hidden, API 403).
- **Negative and robustness**: malformed JSON, wrong types, unknown ids, wrong HTTP methods, logged-out and invalid tokens.
- **Data-driven**: the same CSV drives UI and API validation tests, so both layers enforce the same rules.
- **Consistency across layers**: what the UI shows = what the API returns = what the database stores.

## Test data

Every test creates what it needs with unique values and removes it afterwards (ADR-009, ADR-012). Reference data (the seeded catalogue) is read-only and described in `testdata/products.json`. Setup and clean-up of UI tests go through the API.

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

- **Pull request**: compiles without warnings, Checkstyle clean, unit and smoke green, no new high-severity vulnerable dependency.
- **Main**: build, API, UI and smoke green.
- **Release (nightly before release)**: full suite on Chrome and regression on Firefox and Edge green, Grid run green; failures triaged by report category.

## Out of scope

Performance/load testing, visual regression, accessibility audits and cloud device farms are not part of this framework yet (see "Future enhancements" in the README).
