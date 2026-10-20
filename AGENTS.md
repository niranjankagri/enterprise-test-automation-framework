# Repository Instructions

## Source of truth and scope

- Inspect the current checkout, Git history, tests, configuration and workflows before changing code. Treat an audit or memory note as a hypothesis when the source can be checked.
- Preserve the existing `demo-app` and `automation` Maven modules and the Selenium, TestNG, REST Assured, H2, Allure, Docker/Selenium Grid and GitHub Actions architecture.
- Keep changes limited to the approved remediation chunk. Do not modify other repositories or portfolio projects.

## Code and tests

- Follow [docs/coding-standards.md](docs/coding-standards.md) and the existing package and test-suite conventions.
- Every mechanically testable bug fix needs a focused regression test. Keep assertions meaningful; do not hide failures, delete useful coverage or turn required checks into skips.
- Consider negative cases, concurrency, cleanup, parallel execution and test-data ownership where relevant.
- Use `mvnw.cmd` on Windows or `./mvnw` on POSIX shells. Run the relevant Checkstyle, compilation and TestNG suites; record exact commands, exit codes and test counts. Never report a check as passed unless its result was inspected.
- Run `git diff --check` and critically review the full staged diff before committing.

## Security and evidence

- Never add credentials, real tokens, private employer/client data or sensitive report contents to source, logs, tests, commits or progress files.
- Use synthetic secrets in redaction tests and verify retained evidence does not contain them.
- Preserve useful diagnostics while masking sensitive values at every output boundary.

## Git and review safety

- Before each chunk, verify the branch, HEAD and worktree. Preserve unrelated changes; never reset, clean or overwrite user data to make a check pass.
- Work only on `codex/*` feature branches based on freshly fetched `origin/main`. Stage explicit paths and keep one logical chunk per commit.
- Never commit or push to `main`, force push, rewrite published history, merge a pull request, deploy publicly, alter GitHub secrets or change branch protections.
- Push only locally verified chunks. Keep the batch pull request in draft until its required GitHub Actions checks have reached terminal status and passed. Stop at the owner review gate; do not merge.
- Maintain `docs/CODEX_MASTER_PLAN.md`, `docs/CODEX_PROGRESS.md` and `docs/CODEX_DECISIONS.md` without erasing useful history. After every verified chunk, update the owner's existing Desktop `MEMORY.md` with concise evidence and progress, preserving its existing content. Keep personal machine paths out of repository files.
