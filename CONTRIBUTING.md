# Contributing

## Workflow

1. Create a branch from `main`.
2. Make the change and add or update tests.
3. Run `mvn clean test` locally; it must pass.
4. Open a pull request. CI must be green before merging.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Use for |
|---|---|
| `feat:` | new framework capability or tests |
| `fix:` | bug fix |
| `refactor:` | code change without behaviour change |
| `docs:` | documentation only |
| `ci:` | pipelines, Docker, build infrastructure |
| `chore:` | maintenance, dependencies |

## Code rules

- Tests describe behaviour; Selenium, HTTP and SQL code belongs in the framework (`automation/src/main/java`).
- No `Thread.sleep`; wait for a condition instead.
- Each locator is defined once, in its page or component.
- No credentials, tokens or keys in the code or in Git; use environment variables or CI secrets.
- No empty `catch` blocks.
- Tests are independent: each creates the data it needs and cleans it up.
