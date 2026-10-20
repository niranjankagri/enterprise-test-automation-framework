# Codex Remediation Progress

Updated at each verified chunk. Preserve earlier evidence when updating this file.

## Current batch: A — repository baseline and coding harness

- **Status:** C00 in progress; wrapper, instructions and tracking records are prepared, and local wrapper validation has passed. Git staging/commit/push, draft PR and required Actions verification remain.
- **Branch:** `codex/batch-a-baseline`, based on freshly fetched `origin/main`.
- **Baseline SHA:** `9a415067fcce2bd4e658f37029775853936ab4da`.
- **Result SHA:** pending.
- **Commit / push:** pending.
- **Draft PR:** pending; `gh auth status` currently reports the saved GitHub CLI token is invalid.
- **GitHub Actions:** pending PR creation.

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
- `.github/workflows/ci.yml`, `regression.yml` and `run-suite.yml` now call `./mvnw`; Docker's container-local `mvn` command is unchanged. Local wrapper tests passed; Ubuntu Actions verification is pending.
- Local Maven wrapper tests use a task-specific temp wrapper cache and explicit `maven.repo.local` because of this sandbox's profile mapping; these paths are not committed.
- Final C00 status, exact diff review, `git diff --check`, staged checks, commit, push, draft PR and terminal Actions conclusions remain pending.

## Chunk ledger

| Chunk | Status | Evidence / result |
|---|---|---|
| C00 | In progress | Pinned Maven Wrapper, repository instructions, plan/progress/decision files, README and host-run workflow migration are prepared; CI/PR gate remains pending. |
| C01–C11 | Pending | Do not start until the specified batch dependency and owner review gate are satisfied. |

## Blockers and required follow-up

- Docker Compose service/network validation is **BLOCKED / NOT EXECUTED** until the Docker daemon is available.
- GitHub CLI cannot create a draft PR or inspect Actions because its saved token is invalid. Git fetch over the configured Git remote works; branch push and PR creation still need verification. Use an already authenticated GitHub session if available; otherwise the owner must reauthenticate `gh` without sharing the token.
- Do not mark C00 complete until the feature branch is pushed, the draft PR exists and applicable GitHub Actions jobs have terminal successful conclusions.
