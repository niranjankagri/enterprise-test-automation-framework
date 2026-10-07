# ADR-019: Docker and Selenium Grid for remote execution

Status: accepted

- **Problem:** tests must run the same way outside a developer laptop: on CI agents without browsers, on browsers the developer does not have, and with more parallel sessions than one machine offers.
- **Options:** (a) install browsers and the application on every CI agent; (b) Selenium Grid, hand-installed; (c) the application, a Selenium Grid (hub + Chrome, Firefox, Edge nodes) and the test runner as containers in one `docker compose` file; (d) a cloud browser provider (see ADR-021).
- **Decision:** (c). One multi-stage `docker/Dockerfile` (build, app, tests) and `docker/docker-compose.yml`; `-Dexecution=remote -Dgrid.url=...` switches `DriverFactory` to `RemoteWebDriver` with the same browser options as local runs.
- **Reason:** one command starts an identical platform on any machine with Docker; health checks order the start-up; `--exit-code-from tests` makes it a pipeline gate. Grid is the standard way to run Selenium remotely and works the same with any Grid, containerised or not. Same options everywhere means "works locally, fails on the Grid" cannot come from configuration.
- **Trade-offs:** container start-up adds minutes; browsers in containers reach the application by service name (`http://demo-app:8081`), so URLs differ from local runs; each node has a fixed number of sessions, and extra threads queue (see [grid.md](../grid.md)).

[All decisions](README.md) · [Architecture](../architecture.md)
