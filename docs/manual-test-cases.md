# Manual test cases

The test cases this framework automates on the demo shop (`demo-app`), written as a tester would run
them by hand. Every case has an ID; the TestNG method that covers it starts with the same ID
(`tc229_placingAnOrderComputesTotalsAndReservesStock`), so a failure in the Allure report leads straight
to the case below. Each table row is one case: what to do and what must happen. Data-driven methods are
one case whose data sets come from the files named in the row.

| ID range | Area | Automated in |
|---|---|---|
| TC-101 to TC-142 | Web UI and end-to-end journeys | `automation/src/test/.../tests/ui`, `tests/e2e` |
| TC-201 to TC-266 | REST API | `tests/api` |
| TC-301 to TC-320 | Database, API-to-database, concurrency, UI-API-database | `tests/db`, `tests/integration` |

Not listed: the framework's own tests (`tests/platform`, `tests/foundation`, `DriverFactoryTest`), the API
client's read timeout (`ApiTransportTimeoutTest`), the database helper's own checks
(`DatabaseLayerTest`: bound parameters, single-row queries, failure messages), and two checks that typed
secrets stay out of logs and reports (`LoginRegressionTest.typedPasswordsAreOmittedFromLogsAndReportSteps`,
`ProductCatalogTest.missingCategoryOptionDoesNotPersistRequestedValue`). The requirement-to-test mapping is
in [TEST_COVERAGE_MATRIX.md](TEST_COVERAGE_MATRIX.md).

## Setup

- Build once (`./mvnw -DskipTests package`), start the shop: `java -jar demo-app/target/demo-app.jar`
  (UI <http://localhost:8080/login.html>, API <http://localhost:8080/api>, H2 database on port 9092).
- Users: **admin** / `Admin@12345` (Alex Admin, role ADMIN), **viewer** / `Viewer@12345` (Vera Viewer,
  role VIEWER, read-only).
- API: `POST /api/auth/login` returns a Bearer token; send `Authorization: Bearer <token>`.
- Seeded catalogue (`automation/src/test/resources/testdata/products.json`): Ultrabook 14 ($1,199),
  Workstation 16 ($2,199), 27-inch 4K Monitor ($429), 34-inch Ultrawide Monitor ($649), Wireless Keyboard
  ($79), Wireless Mouse ($49), USB-C Dock ($189), Noise-Cancelling Headphones ($299). Seeded customer:
  Ava Patel, `ava.patel@example.com`, Austin.
- Order status flow: PLACED → SHIPPED → DELIVERED; CANCELLED from PLACED.
- Database cases: connect to `jdbc:h2:tcp://localhost:9092/mem:shop` with any SQL client; tables
  `app_users`, `customers`, `products`, `orders`, `order_items`.
- Cases marked *needs a proxy* change answers on purpose (DevTools › Network › block/override, or a proxy);
  cases marked *needs two clients* send requests at the same moment (two terminals / a load tool).

## Summary by area

| Area | IDs | Cases |
|---|---|---|
| Login and session | TC-101 to TC-109 | 9 |
| Navigation and dashboard | TC-110 to TC-112 | 3 |
| Catalogue and search | TC-113 to TC-119 | 7 |
| Customer management (UI) | TC-120 to TC-130 | 11 |
| Checkout and cart | TC-131 to TC-136 | 6 |
| Purchase journeys (E2E) | TC-137 to TC-142 | 6 |
| Authentication and authorization (API) | TC-201 to TC-212 | 12 |
| Customers (API) | TC-213 to TC-228 | 16 |
| Orders (API) | TC-229 to TC-245 | 17 |
| Products (API) | TC-246 to TC-258 | 13 |
| Users (API) | TC-259 to TC-266 | 8 |
| Database | TC-301 to TC-305 | 5 |
| API to database | TC-306 to TC-311 | 6 |
| Concurrency | TC-312 to TC-317 | 6 |
| UI, API and database together | TC-318 to TC-320 | 3 |

---

## UI: login and session (`LoginSmokeTest`, `LoginRegressionTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-101 | Admin signs in and sees the dashboard | Sign in as admin | Heading "Dashboard", "Welcome back, Alex Admin.", header "Alex Admin (ADMIN)" |
| TC-102 | Wrong password is rejected | Sign in as admin with a wrong password | "Invalid username or password"; still on `/login.html` |
| TC-103 | Signing out returns to the login page | Sign in, click **Sign out** | Login page with "You have been logged out." |
| TC-104 | Sign-in is rejected (5 data sets, `testdata/login-negative.csv`) | Per row: enter username and password, submit. Rows: wrong password; unknown user `nobody`; password with wrong case `admin@12345`; username missing; password missing | Wrong/unknown/case: banner "Invalid username or password". Missing field: "Username is required" / "Password is required" at the field, which is marked invalid (`aria-invalid`, linked error with role alert) and gets focus. Always still on the login page |
| TC-105 | Viewer signs in with the read-only role | Sign in as viewer | Header "Vera Viewer (VIEWER)" |
| TC-106 | Protected pages redirect to login without a session | Without signing in, open a protected page (e.g. `/customers.html`) | Redirect to `/login.html` |
| TC-107 | Login fields have labels and visible keyboard focus | Tab through the login form; run axe DevTools | Every input has a label; focus starts in Username, then Password; a solid focus outline ≥ 1.5 px; no axe violations |
| TC-108 | A deleted account's browser session returns to login | Create a user (API), sign in as them in the browser, delete the user (API), reload | Login page with `expired=1` and "session has expired" |
| TC-109 | An expired session clears the cart and returns to login | Sign in, add Wireless Mouse to the cart, let the session expire (*needs a proxy*: answer the next API call with 401), go to Customers | Login page "session has expired"; Session Storage `shop.token`, `shop.user`, `shop.cart` are empty |

## UI: navigation and dashboard (`NavigationSmokeTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-110 | Every section opens from the menu | As admin open Customers, Products, Orders, Checkout, Dashboard from the menu | Each page shows its heading and its menu entry is highlighted |
| TC-111 | Dashboard shows the key figures | Open the dashboard | Product and customer counts > 0; revenue starts with `$`; recent-orders table columns Order, Customer, Total, Status, Date |
| TC-112 | Dashboard navigation has no axe violations | Run axe DevTools on the dashboard | No violations of the selected rules |

## UI: catalogue and search (`CatalogSmokeTest`, `ProductCatalogTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-113 | Customer search narrows the list | Customers: search `ava.patel@example.com` | Exactly one row: Ava Patel, Austin; count "1 customer" |
| TC-114 | Product search and category filter | Products: filter **Monitors**; then **All categories** and search `keyboard` | Only Monitors in the Category column; then only "Wireless Keyboard" |
| TC-115 | Adding products fills the cart | Add Wireless Mouse twice and USB-C Dock; open the cart | Lines Wireless Mouse (quantity 2) and USB-C Dock; total $287.00 |
| TC-116 | Every product is listed with its details (8 data sets, `products.json`) | Per product: search its SKU | The row shows its name, category and price |
| TC-117 | Each category filter shows exactly its products (data sets: Accessories, Audio, Laptops, Monitors) | Choose the category | Exactly the seeded products of that category, none of another |
| TC-118 | Search without a match shows an empty list | Search `no-such-product-xyz` | Empty table, "0 products" |
| TC-119 | Catalogue failure shows Retry and recovers | *Needs a proxy*: fail the products request, open Products; then let it through and click **Retry** | Loader while busy; "Products could not be loaded" with an enabled **Retry**; after Retry the list (Wireless Keyboard, Wireless Mouse) without a page reload |

## UI: customer management (`CustomerManagementTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-120 | Admin creates a customer | **Add customer**, fill all fields, **Save** | Toast "Customer <name> created"; the row shows name, phone, city, status ACTIVE |
| TC-121 | A customer without optional fields is accepted | Create a customer without phone and city | Saved; Phone and City cells empty |
| TC-122 | Admin edits a customer | **Edit** a customer; change City to Lisbon; save | Form "Edit customer" opens pre-filled; toast "... updated"; row shows Lisbon |
| TC-123 | Admin deletes a customer | **Delete** a customer, confirm | Confirmation names the customer; toast "... deleted"; search finds nothing |
| TC-124 | Cancelling the form creates nothing | Open **Add customer** (check it), fill it, **Cancel** | The dialog is a modal (`role=dialog`, `aria-modal`, title "Add customer"), focus in First name; after Cancel no new customer |
| TC-125 | HTML in a customer name stays inert | Create a customer named e.g. `<img src=x onerror=alert(1)>` | The name shows as literal text; no image element, no script runs |
| TC-126 | Invalid input is rejected per field (8 data sets, `testdata/invalid-customers.csv`) | Per row: fill a valid customer, change one field, save. Rows: first/last name or email missing; email without @ / without domain; phone with letters / too short; first name of 52 characters | The field's message (e.g. "Email must be a valid email address", "Phone must contain 7-20 digits, spaces, +, - or ()", "First name must be at most 50 characters"); form stays open; field marked invalid and focused |
| TC-127 | Customer list and form have no axe violations | Run axe DevTools on the list and on the open Add dialog | No violations |
| TC-128 | Duplicate email is rejected in the form | Add a customer with an existing email | "Email is already in use" at the email field |
| TC-129 | Viewer cannot change customers | Sign in as viewer, open Customers | List visible; no Add, Edit or Delete buttons |
| TC-130 | Customer list failure shows Retry and recovers | *Needs a proxy*: fail the customers request, then Retry | "Customers could not be loaded" with Retry; after Retry the list (with `ava.patel@example.com`) |

## UI: checkout and cart (`CheckoutTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-131 | An empty cart cannot be ordered | Open Checkout with an empty cart | Empty cart, total $0.00, **Place order** disabled |
| TC-132 | Quantities drive the total | Add 27-inch 4K Monitor and Wireless Keyboard; set the monitor's quantity to 3 | Total = 3 × $429 + $79 = $1,366.00; cart badge 4 |
| TC-133 | Removing the last item empties the cart | Add USB-C Dock, remove it in the cart | Cart empty, order disabled, badge 0 |
| TC-134 | An order needs a customer | Add Wireless Mouse, open the cart, **Place order** without choosing a customer | "Please select a customer"; no API call |
| TC-135 | Viewer cannot place orders | As viewer add Wireless Mouse, open the cart | The line is shown; **Place order** disabled |
| TC-136 | Checkout is accessible and keyboard usable | Add Wireless Mouse, open the cart, run axe; Tab from the quantity field | No axe violations; focus moves to **Remove** with a visible solid outline ≥ 1.5 px; ordering enabled |

## E2E: purchase journeys (`PurchaseJourneyTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-137 | Admin places an order for a customer | Sign in; search and add products; open the cart (controls have labels "Customer", "Quantity of ..."); choose the customer; **Place order** | The total matches the products; the new order is listed with the customer's name |
| TC-138 | A stale cart cannot buy consumed stock | Add a product to the cart; *needs two clients*: consume its stock through the API; place the order | Order refused with a message; cart kept; no order created |
| TC-139 | A product deactivated after display cannot be bought | As TC-138, but deactivate the product (API `DELETE /products/{id}`) instead | Same: refused, cart kept, no order |
| TC-140 | Double submit creates one order | Click **Place order** twice quickly | The button is disabled after the first click; exactly one order with quantity 1 |
| TC-141 | A failed submission can be retried without partial writes | *Needs a proxy*: answer the order request with 503 "Synthetic service unavailable" | Button disabled while pending; message "Synthetic service unavailable"; button enabled again; cart kept; no order in API or database; a retry works |
| TC-142 | Admin cancels an order | Place an order (1 unit, the last in stock); open it, **Cancel order** | Database: status PLACED, one line, stock 0; after cancelling: toast "Order #<id> cancelled", status CANCELLED |

---

## API: authentication and authorization (`AuthApiTest`, `AuthServiceExpiryTest`, `AuthorizationRouteMatrixTest`)

| ID | Case | Request | Expected result |
|---|---|---|---|
| TC-201 | Admin logs in and gets a Bearer token | `POST /api/auth/login` as admin | `200` JSON; `tokenType` Bearer, `role` ADMIN, `expiresIn` 3600; matches the login schema |
| TC-202 | Wrong password and unknown user get the same 401 | Login `admin`/`wrong`, then `nobody`/`wrong` | Both `401` "Invalid username or password" |
| TC-203 | Missing credentials: 400 per field | Login with `{}` | `400`; `username`: "Username is required", `password`: "Password is required" |
| TC-204 | The client's request id is used for correlation | `GET /api/customers` with `X-Request-Id: trace-auth-0001`; then with `<script>alert(1)</script>` | First echoed unchanged; the unsafe one replaced by a generated UUID |
| TC-205 | No token: 401 naming problem and path | `GET /api/customers` without a token | `401` "Missing bearer token", path `/api/customers` |
| TC-206 | An unknown or malformed token is 401 | Token `not-a-real-token`, then `%%%not-a-token` | `401` "Invalid or expired token" both |
| TC-207 | A logged-out token stops working | Login as viewer; list customers; `POST /api/auth/logout`; list again | `200`, `204`, then `401` |
| TC-208 | Viewer can read but not write | As viewer: list customers/products; create a customer; delete a product; change an order status; list users | Reads `200`; every write and the users list `403` "This action needs the ADMIN role" |
| TC-209 | `/users/me` never returns a password | As viewer `GET /api/users/me` | `200`, role VIEWER, no "password" in the body |
| TC-210 | Tokens expire, sessions are bounded | Sign in; wait past the expiry (or move the clock); also open more than 100 sessions for one user | Expired token `401`; the oldest session is evicted (`401`) while the newest still works |
| TC-211 | Every protected route enforces the role matrix | For each route call it as anonymous, viewer and admin (valid ids and bodies) | The expected status per role (401 / 403 / 2xx); denied writes change nothing |
| TC-212 | User list and detail omit credentials | As admin `GET /api/users` and `GET /api/users/{id}` | No `passwordHash`, `salt` or supplied password in any user |

## API: customers (`CustomerApiTest`, `FinalHardeningApiTest`)

| ID | Case | Request | Expected result |
|---|---|---|---|
| TC-213 | POST creates a customer | `POST /api/customers` with a valid customer | `201`; `Location: /api/customers/{id}`; `X-Request-Id`; the data as sent, status ACTIVE |
| TC-214 | GET returns the customer | `GET /api/customers/{id}` | Exactly what POST returned |
| TC-215 | Search by email, name and city | `GET /api/customers?search=<email>`; city in upper case; `no-such-customer-xyz` | The customer (case-insensitive); empty for no match |
| TC-216 | PUT replaces the whole customer | `PUT` with new data, no phone, status INACTIVE | `200`; same id and createdAt; phone null; status INACTIVE |
| TC-217 | PATCH changes only the given field | `PATCH {"city":"Oslo"}` | `200`; city Oslo, everything else unchanged |
| TC-218 | DELETE removes the customer | `DELETE /api/customers/{id}` | `204` empty body; then GET `404` "Customer {id} not found" |
| TC-219 | Invalid fields: 400 with the field message (8 data sets, `invalid-customers.csv`) | Valid body with one field changed per row | `400` "Validation failed"; the field's message, same as in the UI (TC-126) |
| TC-220 | All invalid fields reported together | `POST {"email":"bad","status":"UNKNOWN"}` | `400` with exactly firstName, lastName, email, status |
| TC-221 | Duplicate email: 409 | Create with an existing email (other letter case) | `409`, email "Email is already in use" |
| TC-222 | A customer with orders cannot be deleted | Place an order for the customer; DELETE it | `409` "... has orders" |
| TC-223 | Unknown ids: 404 | GET, PATCH, DELETE `/api/customers/999999` | `404` each |
| TC-224 | Malformed requests: 400 or 405, never 500 | `GET /customers/abc`; POST `{not json`; POST `[1, 2]`; `PUT /customers` | "Path parameter 'id' must be a number: abc"; "Malformed JSON request body"; "Request body must be a JSON object"; `405` |
| TC-225 | Explicit null in PATCH clears optional fields | `PATCH {"phone":null,"city":null}` | Phone and city null; email unchanged |
| TC-226 | Simultaneous duplicate creation: one 201, one 409 | *Needs two clients*: create the same customer twice at once | One `201`, one `409`; one stored customer |
| TC-227 | Concurrent PATCHes keep both fields | *Needs two clients*: PATCH city and phone at once | Both `200`; city "Patch City" and phone "+1-555-0199" both stored |
| TC-228 | An oversized request is rejected | POST a customer body larger than the limit | `413`; nothing stored |

## API: orders (`OrderApiTest`)

Fixture per case: a new customer; product A ($10.50, stock 5) and product B ($4.00, stock 3).

| ID | Case | Request | Expected result |
|---|---|---|---|
| TC-229 | Placing an order computes totals and reserves stock | `POST /api/orders` 2 × A + 1 × B | `201`, `Location`; PLACED; total 25.00, lines 21.00 and 4.00; stock A 3, B 2 |
| TC-230 | Duplicate product lines stay separate | Order 2 × A and 3 × A | Lines 2 and 3 (21.00, 31.50), total 52.50; stock A 0 |
| TC-231 | Orders filtered by customer and status | `GET /api/orders?customerId=...`; with `status=SHIPPED` | The customer's order; empty for SHIPPED |
| TC-232 | Status follows the allowed flow | Move PLACED → SHIPPED → DELIVERED → CANCELLED | First two `200`; last `409` "cannot go from DELIVERED to CANCELLED" |
| TC-233 | Cancelling gives the stock back | Order 4 × A (stock 1), cancel | Stock A back to 5 |
| TC-234 | More than the stock: 409, nothing changes | Order B and 6 × A | `409` "Insufficient stock for <sku>: 5 left, 6 requested"; B's stock unchanged; no order |
| TC-235 | Invalid orders: 400 per field | `{customerId:null, items:[]}`; quantity 0 | "Customer is required", "At least one item is required"; `items[0].quantity` message |
| TC-236 | Invalid quantity shapes rejected (12 data sets) | Quantity: missing, null, 0, -1, minimum int, 1.5, "2", "abc", true, {}, [], 2147483648 | `400` on `items[0].quantity`; stock 5 and no order |
| TC-237 | Maximum int quantity is a stock conflict | Quantity 2147483647 | `409` "5 left, 2147483647 requested"; nothing changes |
| TC-238 | An overflowing later line rolls back every line | Valid line B, then A with an overflowing quantity | `400` on `items[1].quantity`; stock unchanged; no order |
| TC-239 | Unknown customer or product: 404 | Order for customer 999999; for product 999999 | `404` both; stock unchanged |
| TC-240 | An inactive customer cannot order | Set the customer INACTIVE, order | `409` "Customer {id} is inactive" |
| TC-241 | Viewer reads orders but cannot place them | As viewer GET an order; POST an order | `200`; `403` |
| TC-242 | The maximum order total is stored exactly | Order products adding up to 9,999,999,999.99 | Accepted; database total exactly 9999999999.99; stock 0 |
| TC-243 | A total above the database maximum is rejected | Order 101 units of a high-priced product | `400` "Order total must be at most 9999999999.99"; no order, no lines, stock unchanged |
| TC-244 | An overflowing product id is rejected | Product id = 2⁶⁴ + A's id | `400` on `items[0].productId`; nothing changes |
| TC-245 | Too many order lines are rejected before writes | Order with more lines than the limit | `400` on `items`; nothing changes |

## API: products (`ProductApiTest`)

| ID | Case | Request | Expected result |
|---|---|---|---|
| TC-246 | Catalogue matches the reference data (8 data sets) | Per SKU: find it, `GET /api/products/{id}` | Matches the product schema; name, category, price as in `products.json`; active |
| TC-247 | A new product is listed in its category | Create a product; `GET /api/products?category=...` | Stored as sent; listed in its category |
| TC-248 | PATCH updates price and stock | `PATCH {"price":19.99,"stock":0}` | Price 19.99, stock 0, name unchanged |
| TC-249 | PUT replaces the product | `PUT` with new SKU and name | Both changed |
| TC-250 | DELETE deactivates instead of removing | `DELETE /api/products/{id}` | `204`; still readable by id with `active:false`; not found by search |
| TC-251 | Invalid values: 400 per field | Empty SKU, no name, negative price and stock | "SKU is required", "Name is required", "Price must be a positive number", "Stock must be a whole number of 0 or more" |
| TC-252 | Duplicate SKU: 409 | Create with an existing SKU | `409`, sku "SKU is already in use" |
| TC-253 | SQL-shaped search is treated as data | `GET /api/products?search=' OR '1'='1 --` | No products (the query is not broadened) |
| TC-254 | More than two decimal places is rejected | Create with price `1.001` | `400` on price; no product row |
| TC-255 | A price above the database maximum is rejected | Price `100000000.00` (above 99,999,999.99) | `400` on price; no row |
| TC-256 | The maximum price is stored exactly | Price 99999999.99 | `201`; database price exactly 99999999.99 |
| TC-257 | Trailing zeroes are accepted and normalized | Price `1.000` | `201`; stored 1.00 |
| TC-258 | A PATCH with sub-cent precision changes nothing | Product at 10.00; PATCH price `10.001` | `400`; database price still 10.00 |

## API: users (`UserApiTest`)

| ID | Case | Request | Expected result |
|---|---|---|---|
| TC-259 | Admin creates a user who can sign in with their role | Create a VIEWER user; sign in as them; create a customer | `201` without the password in the body; their name from `/users/me`; the write is `403` |
| TC-260 | Promoting a user grants write access | PATCH role ADMIN; sign in again; list users | Role ADMIN; users list `200` |
| TC-261 | Admin lists users | `GET /api/users` | At least the two seeded accounts |
| TC-262 | Invalid and duplicate users are rejected | Short password, bad role; existing username | "Password must be at least 8 characters" and a role error; "Username is already taken" |
| TC-263 | Admin cannot delete their own account | DELETE own id | `409` "You cannot delete your own account" |
| TC-264 | A deleted user cannot sign in | Sign in twice (two tokens), delete the user | Both tokens `401` afterwards; new login `401` |
| TC-265 | Login racing deletion cannot keep access | *Needs two clients*: login, an authenticated call and the deletion at once | Each may succeed first, but afterwards every token is `401` |
| TC-266 | Demoting a user revokes their admin sessions | Two admin sessions; PATCH role VIEWER | Both old tokens `401`; a new login lists users `403` but reads customers `200` |

---

## Database (`DatabaseLayerTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-301 | The schema has the application's five tables | List the tables | Exactly `app_users`, `customers`, `order_items`, `orders`, `products` |
| TC-302 | Product rows match the reference data (8 data sets) | `SELECT * FROM products WHERE sku = ?` per SKU | Name, category, price as in `products.json` |
| TC-303 | Catalogue size matches the reference file | `SELECT COUNT(*) FROM products` (seeded) | 8 |
| TC-304 | Unique constraints reject duplicates | Insert user `admin`, customer `ava.patel@example.com`, product `ACC-3002` again | Constraint violation each; still one row each |
| TC-305 | Foreign keys protect references and cascade lines | Insert an order for a missing customer, a line for a missing order; delete a referenced customer/product; delete an order | Violations for the first four; deleting the order also deletes its lines |

## API to database (`ApiToDatabaseTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-306 | A created customer is stored exactly as sent | Create through the API; read the row | Every column as sent; status ACTIVE |
| TC-307 | Updates and deletes reach the database | PATCH city Lyon, status INACTIVE; then DELETE | Row shows Lyon/INACTIVE; then no row |
| TC-308 | A placed order is stored with its lines and reserves stock | Product stock 10 at $12.25; order 3 | Order row PLACED with the total; one line, quantity 3; stock 7 |
| TC-309 | Cancelling and deleting orders give stock back | Stock 4; order 2 (cancel it) and 1 (delete it) | Stock 1 after both orders, 4 after cancel + delete; cancelled row CANCELLED; deleted order and its lines gone |
| TC-310 | A rejected order leaves no trace | Order 5 of a plentiful product and 2 of one with stock 1 | No order row; the plentiful product's stock still 50 |
| TC-311 | Passwords are stored hashed and salted | Create two users with the same password; read `app_users` | No plain-text password; `password_hash` is 64 hex characters; the two hashes differ |

## Concurrency (`OrderReservationConcurrencyTest`)

All *need two clients* (the automated test holds a row lock so both requests really overlap).

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-312 | Competing orders cannot oversell | Shared product with stock 3; two orders of 2 each at once | One `201`, one `409`; no partial reservation |
| TC-313 | Orders with lines in reverse order do not deadlock | Two orders for products X, Y in opposite line order, at once | Both finish (no deadlock) |
| TC-314 | Cancellations with reversed lines do not deadlock | Cancel two such orders at once | Both finish |
| TC-315 | Two cancellations restore stock once | Cancel the same order twice at once | Stock returned exactly once |
| TC-316 | Two deletions restore stock once | Delete the same order twice at once | Stock returned exactly once |
| TC-317 | Cancellation racing deletion restores stock once | Cancel and delete the same order at once | Stock returned exactly once |

## UI, API and database together (`UiApiDatabaseTest`)

| ID | Case | Steps | Expected result |
|---|---|---|---|
| TC-318 | A customer created through the API is stored and visible in the UI | Create through the API → check the response → check the row → search in the UI | ACTIVE everywhere; the UI row shows name, city, status |
| TC-319 | A customer changed in the UI is visible through the API and stored | Create in the UI; edit City Kyoto, Status Inactive | API shows Kyoto/INACTIVE; row status INACTIVE |
| TC-320 | An order placed in the UI is consistent in API and database | Order 2 of a product through the UI | API: same total and SKU; database: total, PLACED, one line of 2; stock down by 2 |
