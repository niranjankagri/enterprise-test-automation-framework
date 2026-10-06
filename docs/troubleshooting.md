# Troubleshooting

Start with the evidence: the Allure report (failure category, steps, screenshot, request/response, test log) or `automation/target/logs/automation.log` (every line has `[thread] [Test.method]`; failures have a `FAILURE DIAGNOSTICS` block).

| Symptom | Likely cause | What to do |
|---|---|---|
| `Unknown environment ... -Denv=local\|qa\|staging` | typo in `-Denv` / `ENV` | use one of the listed names, or add `config/<name>.properties` |
| `Configuration 'admin.password' is not set for environment 'staging'` | secret not provided | set `ADMIN_PASSWORD` / `VIEWER_PASSWORD` (CI secret or `.env`) |
| `Cannot start the demo app on port 8080` / `Address already in use` | another process uses 8080 or 9092 | stop it, or run against a separately started app: `-Denv=qa` |
| Database tests are **skipped** with "No database access" | the environment has no `db.url` | expected for `staging`; set `DB_URL` to run them |
| `SessionNotCreatedException` / "Chrome instance exited" | browser could not start (CI sandbox, missing browser, Grid without free node) | the framework retries start-up once; on CI `--no-sandbox` is added automatically; check the Grid at `http://<grid>:4444/ui` |
| `TimeoutException: Expected condition failed: ...` | the expected UI state never appeared (slow app, changed markup, real defect) | read the condition in the message, check the screenshot and page source; never "fix" with a sleep |
| Clicks seem to do nothing on Chrome right after signing in | Chrome's password-leak dialog (browser UI, invisible in screenshots) | already disabled in `BrowserOptionsFactory`; keep those prefs when changing options |
| Number input does not change on Firefox | Ctrl+A does not select in number inputs | use `CheckoutPage.setQuantity` (End + Backspace) as the pattern |
| A test passes alone but fails in parallel | shared state: instance fields with `parallel=methods`, assertions on shared lists | keep `parallel=classes` or move state to `ThreadLocal`; assert only on the test's own data |
| `Clean-up failed (...)` warnings | the test already removed its data, or the app was unreachable | clean-ups never fail a test; make the clean-up idempotent (`deleteCustomerIfExists`) |
| Checkstyle fails the build | a rule in `config/checkstyle.xml` (sleep, empty catch, unused import...) | fix the code; the message names the rule |
| Every test class runs, listeners do not, `Connection refused` everywhere; warning `suiteXmlFiles ... not supported after 3.6.0` | Surefire 3.6+ ignores TestNG suite files | keep `maven-surefire-plugin.version` on 3.5.x (Dependabot is told to skip 3.6+) |
| `warnings found and -Werror specified` in CI | a compiler warning (e.g. deprecated API) | run `mvn compile` without `-q` locally to see the warning |
| Allure report is empty | results not written / wrong folder | results are in `automation/target/allure-results`; run `npx allure-commandline serve automation/target/allure-results` |
| `@Step` annotations have no effect | AspectJ weaver does not run on JDK 27 | use `Report.step(...)`; the framework does not use `@Step` |
| Docker run: tests cannot reach the app | browsers run in node containers | inside compose use `http://app:8081`, not `localhost` (already set in `docker-compose.yml`) |

## Debugging a single test

```bash
mvn test -Dsuite=ui -Dparallel=none -Dheadless=false
```

Run the suite that contains the test serially and with a visible browser; the log file then reads top to bottom, one test at a time. Do not use `-Dtest=...` with Maven: it bypasses the suite files, so the listeners (including the one that starts the application) do not run. In the IDE, run a suite file from `src/test/resources/suites` with the same `-D` options.
