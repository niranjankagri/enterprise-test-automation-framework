# Docker

Application under test, Selenium Grid (hub + Chrome, Firefox and Edge nodes) and the test runner in containers:

```bash
# from the repository root
docker compose -f docker/docker-compose.yml up --build --abort-on-container-exit --exit-code-from tests
```

Full description (variables, image stages, services, CI use, troubleshooting): [docs/docker.md](../docs/docker.md). Selenium Grid: [docs/grid.md](../docs/grid.md).
