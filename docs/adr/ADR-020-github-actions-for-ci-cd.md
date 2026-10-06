# ADR-020: GitHub Actions for CI/CD

Status: accepted

- **Problem:** every change must be compiled, checked and tested automatically, with fast feedback on pull requests, broader coverage on the main branch and everything every night, and with evidence downloadable when something fails.
- **Options:** (a) Jenkins; (b) GitLab CI; (c) GitHub Actions.
- **Decision:** (c): `ci.yml` (pull requests: compile with warnings as errors, Checkstyle, unit tests, smoke, dependency review), `regression.yml` ("Main": build, API, UI, smoke on Edge), `nightly.yml` (full suite on Chrome, regression on Firefox and Edge, regression on Docker + Grid), reusable `run-suite.yml` and `allure-report.yml`, and Dependabot for grouped dependency updates.
- **Reason:** the code lives on GitHub, so Actions needs no server; hosted runners have Chrome, Firefox, Edge and Docker; reusable workflows keep the suite-running steps in one place; artifacts (Allure results and report, screenshots, logs, metadata) are uploaded with `if: always()`, so failed runs keep their evidence. Secrets come from repository secrets, never from files.
- **Trade-offs:** tied to GitHub; the steps are plain Maven and Docker commands, so moving to Jenkins or GitLab means rewriting YAML, not the framework. Hosted runners are shared machines, so timings vary and browser start-up occasionally fails; that is what the infrastructure-only retry (ADR-014) is for.

[All decisions](README.md) · [Architecture](../architecture.md)
