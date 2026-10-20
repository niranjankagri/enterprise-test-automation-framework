# Codex Engineering Decisions

Append material decisions with their evidence and trade-offs. Do not record credentials or sensitive report contents.

## D-001 — Keep the audited baseline and use a feature branch

- **Decision:** Start Batch A from freshly fetched `origin/main` at `9a415067fcce2bd4e658f37029775853936ab4da` on `codex/batch-a-baseline`.
- **Reason:** The fetched default branch still matched the requested audit baseline and the worktree was clean. No local commits needed to be preserved.
- **Trade-off:** Later batches must wait for the owner review/merge gate rather than depending on unmerged prior batches.

## D-002 — Use Apache Maven Wrapper in `only-script` mode

- **Decision:** Pin the repository wrapper to Apache Maven 3.9.16 using Maven Wrapper Plugin 3.3.4 and `distributionType=only-script`.
- **Provenance:** Downloaded the binary archive, `.sha512` and `.asc` from `https://downloads.apache.org/maven/maven-3/3.9.16/binaries/` and the key list from `https://downloads.apache.org/maven/KEYS`. Published SHA-512 `ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3` matched the calculated digest. GPG reported a valid detached signature from Slawomir Jaranowski, fingerprint `84789D24DF77A32433CE1F079EB80E92EB2135B1`. The archive's calculated SHA-256 is pinned as `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`; the wrapper downloads from Maven Central over HTTPS.
- **Reason:** The wrapper supplies a repeatable Maven version without requiring developers to install Maven; `only-script` avoids a binary `maven-wrapper.jar` in the repository while the generated scripts still enforce the pinned distribution checksum.
- **Verification:** Fresh-cache `mvnw.cmd -version` and Git Bash `./mvnw -version` both downloaded/verified and reported Maven 3.9.16. The wrapper ran the CI unit command successfully on Windows. GitHub Actions will validate the POSIX wrapper on Ubuntu through the draft PR.
- **Trade-off:** First use requires network access to Maven Central. Existing Docker image builds continue using their pinned Maven base image and container-local `mvn` command.

## D-003 — Migrate host GitHub Actions Maven commands to the wrapper

- **Decision:** Use `./mvnw` for host-run Maven commands in `ci.yml`, `regression.yml` and `run-suite.yml`; retain the Docker container's existing Maven command.
- **Reason:** The wrapper entry point has passed equivalent local Windows and Git Bash validation. The PR workflow must exercise the POSIX wrapper on Ubuntu before C00 can be considered verified.
- **Trade-off:** Workflow verification remains a required external gate; any failure must be fixed in a separate commit without weakening tests.

## D-004 — Synchronize the requested Desktop memory file after each chunk

- **Decision:** After each verified chunk, update the owner's same existing Desktop `MEMORY.md` with concise status, evidence, commit/PR/CI references and the next chunk, preserving all existing content. Keep personal machine paths out of repository files.
- **Reason:** The owner explicitly asked to keep this work collected in the existing memory file and to follow the same small, incremental pattern used on earlier projects.
- **Trade-off:** The Desktop file is outside the repository workspace and requires elevated filesystem access; the repository's `docs/CODEX_*` files remain the versioned project record.

## D-005 — Pin wrapper line endings

- **Decision:** Keep `mvnw` as LF and `mvnw.cmd` as CRLF with repository `.gitattributes` rules.
- **Reason:** The Windows Git configuration has `core.autocrlf=true`; the POSIX wrapper must not be rewritten with CRLF when checked out on Windows and then executed in Linux CI.
- **Verification:** Git records `mvnw` as executable (`100755`); wrapper scripts were exercised from Windows Command Prompt and Git Bash.

## D-006 - Keep Compose services internal and limit host access

- **Decision:** Publish the demo application on `127.0.0.1:8081`, leave H2 TCP 9093 available only on the Compose network, and publish Selenium Grid on `127.0.0.1:4444`.
- **Evidence:** the test runner uses `demo-app:9093` and browsers use `demo-app:8081`; runtime integration passed with H2 and Grid available internally. Container inspection showed only loopback host bindings, and no host listener accepted connections on 9093.
- **Trade-off:** local host tools can use the app and Grid through loopback, while external machines cannot reach those ports directly. Host-based H2 access is intentionally unavailable.

## D-007 - Sanitize Allure output at the reporting boundary

- **Decision:** Mask URL userinfo, sensitive values, report step/attachment labels and textual attachment content at the shared `Report` boundary. Before recording an Allure step failure, mask message, trace, actual and expected status details, then rethrow the original throwable.
- **Reason:** API filters, UI navigation and failure listeners all send their evidence through `Report`, but Allure's normal failure handling stored the original exception details before text attachments could be sanitized. URL usernames can also be sensitive independently of passwords.
- **Verification:** synthetic API/UI regression coverage inspected active Allure metadata and persisted attachments, asserted exception identity preservation, and scanned 89 text artifacts with no synthetic-secret matches.
- **Trade-off:** complete URL userinfo is hidden, while hosts, ports, paths and nonsensitive query values remain visible for diagnosis. Allure step lifecycle handling is explicit so exception metadata can be sanitized before it is stored.
