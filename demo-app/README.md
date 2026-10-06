# ShopEase Admin (application under test)

A small shop back office that the framework tests through its **UI**, its **REST API** and its **database**. It lives in this repository so all three layers can be validated against the same data (see `docs/architecture.md`, ADR-002). It is deliberately simple: the JDK HTTP server, plain JDBC on H2, and Jackson.

## Run it

```bash
mvn -pl demo-app package
java -jar demo-app/target/demo-app.jar --port 8081 --db-port 9093
```

Open http://localhost:8081. For `-Denv=local` the test suite starts it automatically.

| Setting | Option / environment variable | Default |
|---|---|---|
| HTTP port | `--port` / `PORT` | 8080 |
| Database TCP port (JDBC for tests) | `--db-port` / `DB_TCP_PORT` | 9092 |
| Accept DB connections from other hosts | `DB_ALLOW_REMOTE` | false |
| Simulated API latency (ms) | `LATENCY_MS` | 100 |
| Demo account passwords | `ADMIN_PASSWORD`, `VIEWER_PASSWORD` | `Admin@12345`, `Viewer@12345` |

Accounts: `admin` (role ADMIN, full access) and `viewer` (role VIEWER, read-only). The database is in memory and is recreated with 8 products and 3 customers on every start.

## API

All endpoints except login need `Authorization: Bearer <token>`. Errors share one shape: `{status, error, message, path, timestamp, fieldErrors?}`. Every response has an `X-Request-Id` header: the client's own (letters, digits, dashes, at most 64) or a new UUID.

| Method | Path | Role | Notes |
|---|---|---|---|
| POST | `/api/auth/login` | public | `{username, password}` → `{token, tokenType, expiresIn, username, fullName, role}` |
| POST | `/api/auth/logout` | any | token stops working |
| GET | `/api/stats` | any | dashboard counts and revenue |
| GET | `/api/users/me` | any | the signed-in user |
| GET, POST | `/api/users` | ADMIN | |
| GET, PATCH, DELETE | `/api/users/{id}` | ADMIN | cannot delete yourself (409) |
| GET, POST | `/api/customers` | GET any, POST ADMIN | `?search=` name/email/city; duplicate email → 409 |
| GET, PUT, PATCH, DELETE | `/api/customers/{id}` | GET any, rest ADMIN | delete with orders → 409 |
| GET, POST | `/api/products` | GET any, POST ADMIN | `?search=&category=&includeInactive=` |
| GET, PUT, PATCH, DELETE | `/api/products/{id}` | GET any, rest ADMIN | delete = deactivate |
| GET, POST | `/api/orders` | GET any, POST ADMIN | `?customerId=&status=`; POST checks and reserves stock |
| GET, PATCH, DELETE | `/api/orders/{id}` | GET any, rest ADMIN | PATCH `{status}`: PLACED → SHIPPED → DELIVERED, PLACED → CANCELLED |

## Database

Tables `app_users`, `customers`, `products`, `orders`, `order_items` (`src/main/resources/db/schema.sql`). JDBC URL for tests: `jdbc:h2:tcp://localhost:<db-port>/mem:shop` (user `sa`, empty password).
