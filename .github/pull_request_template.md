## Summary

<!-- Briefly describe the change and why it is needed. -->

## Checks

CI runs `./gradlew check`, which includes:

- **Checkstyle** (main and test sources)
- **PMD** (main and test sources)
- **SpotBugs** (main and test bytecode)
- **Error Prone** (during Java compilation)
- **JUnit** tests

Please run `./gradlew check` locally before opening or updating a PR when you touch Java or build config.
