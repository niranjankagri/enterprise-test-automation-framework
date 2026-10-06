# ADR-013: Suites say what runs, configuration says how

Status: accepted

- **Problem:** the same tests run as a quick PR gate, a nightly regression, serially for debugging and with many threads in CI. Encoding parallelism in XML means one XML file per combination.
- **Decision:** one suite file per purpose (`smoke`, `sanity`, `regression`, `api`, `ui`, `integration`, `e2e`, `full`) selecting groups; `ExecutionSettingsListener` (an `IAlterSuiteListener`) applies `parallel`/`threads` from configuration to whichever suite runs. Listeners are declared once per suite file.
- **Reason:** eight small files instead of a matrix; CI changes threads with a variable.
- **Trade-offs:** the listener list is repeated in each suite file (TestNG has no include mechanism for listeners in XML).

[All decisions](README.md) · [Architecture](../architecture.md)
