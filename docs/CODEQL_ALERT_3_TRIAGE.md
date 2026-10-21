# CodeQL alert #3 triage

## Finding

- Alert: [#3, `java/sensitive-log`](https://github.com/niranjankagri/enterprise-test-automation-framework/security/code-scanning/3)
- Baseline: `main` SHA `23e7878bb4b7958a4124234fe6319c03397e03e8`
- Analysis: [CodeQL run 39553677330](https://github.com/niranjankagri/enterprise-test-automation-framework/actions/runs/39553677330), CodeQL 2.27.2, analysis id `1994238458`
- Source location reported by the alert: `LoginPage.java:66`, columns 22-30
- Sink location reported by the alert: `ElementActions.java:51`, columns 35-42
- Alert status: open; no rule suppression or dismissal has been applied.

## Source-to-sink trace

The source span is the identifier `PASSWORD` in `actions.type(PASSWORD, password)`, not the second argument that contains the user-entered password. `PASSWORD` is a static `By` locator initialized as `TestId.of("password")`; that helper constructs the constant CSS selector `[data-testid='password']`.

The values pass through the call as separate arguments:

1. `LoginPage.submit(username, password)` calls `actions.type(PASSWORD, password)`.
2. `ElementActions.type(By locator, String text)` receives the locator as argument one and the entered value as argument two.
3. The reported sink is `LOG.debug("type into {}", locator)`. Its only formatted value is `locator`, whose value is the constant password-field selector. It does not format or concatenate `text`.
4. `text` is used only by `element.sendKeys(text)` inside the report step. The step name is constructed from the constant `[redacted]` label and `describe(locator)`; it does not include `text`.

The other `ElementActions.type` callers use constant test-id locators for product and customer search fields. No caller constructs a locator from the password value. The configured file/console log pattern passes messages through `MaskingConverter`; the per-test `TestLogAppender` also applies `SecretMasker`. Allure step names and failure details are sanitized by `Report` and the final-write listener.

The repository CodeQL workflow does not retain its query database or SARIF as a downloadable artifact. The alert and analysis REST responses expose the source/sink locations and message but not serialized intermediate `codeFlow` steps; the local CodeQL CLI is not installed. The trace above was therefore reconstructed against the exact analyzed source revision, including argument mapping and every use of `text` in the method. This classification is based on that source trace and regression evidence, not on the successful CodeQL job conclusion.

## Assessment and proposed disposition

At the reported call site, the password data cannot reach the logged locator: the highlighted source is a field locator whose value is a constant selector, while the sensitive string is the distinct second parameter. This instance is a false positive caused by the sensitive name of the `PASSWORD` locator.

Keep `java/sensitive-log` enabled. The regression `LoginRegressionTest.typedPasswordsAreOmittedFromLogsAndReportSteps` types a synthetic marker into the password field, verifies the value reached the field, and asserts it is absent from the actual `ElementActions` log event and Allure step names. `ReportRedactionTest.apiAndUiEvidenceRedactsUrlSecretsBeforeAllurePersistsThem` also puts a synthetic `password=` value in failure details, verifies it is masked in report evidence, and verifies the original exception is rethrown unchanged.

After reviewing this trace and the regression evidence, the repository owner may dismiss alert #3 as a false positive. The alert has intentionally not been dismissed as part of this triage PR.
