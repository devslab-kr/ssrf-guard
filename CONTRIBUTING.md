# Contributing

Thanks for considering a contribution to ssrf-guard.

## Development setup

```bash
git clone https://github.com/devslab-kr/ssrf-guard
cd ssrf-guard
./gradlew build
```

Java 21+ is required. The Gradle wrapper handles everything else.

## Tests

```bash
./gradlew test
```

The suite is a mix of unit tests (`NetUtil`, `SsrfGuardInterceptor`, `SafeDnsResolver`) and Spring-context integration tests (`SsrfGuardAutoConfigurationTest`, `SsrfGuardIntegrationTest`). The integration test boots a Spring Boot context and drives a real HTTP call through `MockWebServer`, so the four-layer defense is exercised end-to-end.

Coverage report:

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## Code style

- Java 21, Spring Boot 3.5+ idioms.
- Lombok is used (`@Data`, `@RequiredArgsConstructor`); keep new code consistent.
- `-Xlint:all` + `-Werror` is enabled — fix warnings, don't suppress them unless there's a clear reason and a comment explaining why.
- The `-parameters` compile flag is on for AOP-readable parameter names. Don't disable it.

## Pull-request flow

1. Fork the repo and create a feature branch (`git checkout -b feat/your-feature`).
2. Make your change. Add or update tests — every PR with a code change should add at least one test that fails without the change.
3. Update [CHANGELOG.md](CHANGELOG.md) under `[Unreleased]` with a one-line summary.
4. Run `./gradlew build` locally — the CI workflow runs the same task, so green locally usually means green in CI.
5. Open a PR against `main`. The CI badge has to be green before merge.

## Reporting issues

Open a [GitHub issue](https://github.com/devslab-kr/ssrf-guard/issues). For security-sensitive reports (a bypass or class of bypass we don't cover), email <support@devslab.kr> directly — public issues are fine for everything else.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0 (see [LICENSE](LICENSE)).
