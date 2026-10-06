# Docker

Runs the whole platform in containers: the application under test, a Selenium Grid (hub + Chrome, Firefox and Edge nodes) and the test runner.

```bash
# from the repository root
docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit --exit-code-from tests

# another suite / browser / thread count
SUITE=regression BROWSER=firefox THREADS=4 docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit --exit-code-from tests

# clean up
docker compose -f docker/docker-compose.yml down
```

`--exit-code-from tests` makes the command fail when tests fail, so it can gate a pipeline. Results (Allure results, logs, screenshots) are written to `automation/target` on the host.

| Service | Image | Purpose |
|---|---|---|
| `app` | `docker/Dockerfile` target `app` | ShopEase Admin on port 8081, database TCP port 9093 (reachable from other containers) |
| `selenium-hub` | `selenium/hub` | Grid entry point on port 4444; healthy once a node can start sessions |
| `chrome`, `firefox`, `edge` | `selenium/node-*` | browser nodes, 2 sessions each |
| `tests` | `docker/Dockerfile` target `tests` | `mvn test` with `ENV=qa`, `EXECUTION=remote`, URLs pointing at the `app` service |

Inside the network the browsers reach the application as `http://app:8081`, which is why the test container overrides `BASE_URL`, `API_BASE_URL` and `DB_URL`. The Selenium image tag comes from `SELENIUM_IMAGE_TAG` (default `latest`); pin it in CI for reproducible runs.

## Without Docker

Any Selenium Grid works the same way:

```bash
java -jar selenium-server-<version>.jar standalone --port 4444    # one-node Grid on this machine
mvn clean test -Dexecution=remote -Dgrid.url=http://localhost:4444 -Dbrowser=firefox
```
