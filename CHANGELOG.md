# Changelog

The full changelog with anchored links is published at
**<https://ssrf-guard.devslab.kr/changelog/>** ([한국어](https://ssrf-guard.devslab.kr/ko/changelog/)).

The source of truth for the entries below is [docs/changelog.md](docs/changelog.md) — please update there.

## [Unreleased]

## [3.0.0] — Multi-module + LLM agent SSRF defense

Split into client-specific modules and added Spring AI Tool URL validation. Full notes in [docs/changelog.md](docs/changelog.md#300--multi-module--llm-agent-ssrf-defense).

### Added

- New modules: `ssrf-guard-resttemplate`, `ssrf-guard-webclient`, `ssrf-guard-feign`, **`ssrf-guard-springai`**, `ssrf-guard-jdkhttp`, `ssrf-guard-okhttp`.
- IP-literal host rejection (decimal, hex, octal, partial, IPv6).
- Userinfo (`user:pass@host`) rejection.
- IPv4-mapped IPv6 + 6to4 unmapping (catches `::ffff:10.0.0.5` and `2002:0a00::`).
- Micrometer metrics (`ssrf_guard_blocked_total`, `ssrf_guard_allowed_total`) auto-wired when `MeterRegistry` is on the classpath.
- Structured WARN logs on every block decision.

### Changed — BREAKING

- Package renames — `kr.devslab.ssrfguard.security.*` types split across `-core`, `-httpclient5`, `-restclient`. Full mapping in [docs/changelog.md](docs/changelog.md#300--multi-module--llm-agent-ssrf-defense).
- `SecurityException` → `SsrfGuardException` (still a `SecurityException` subclass).

## [2.0.0] — Rebrand to `kr.devslab:ssrf-guard`

### Changed

- **BREAKING — coordinate changed.** From `com.devs.lab:ssrf-guard-spring-boot-starter` to `kr.devslab:ssrf-guard`. The legacy artifact was never published to Maven Central, so v2.0.0 is the first proper Central release.
- **BREAKING — package renamed.** `devs.lab.ssrf.*` → `kr.devslab.ssrfguard.*`. Full mapping in [docs/changelog.md](docs/changelog.md#200--rebrand-to-krdevslabssrf-guard).
- **BREAKING — `SsrfGuardApplication` (the empty `@SpringBootApplication`) removed.** Vestigial scaffolding from the original Spring Initializr template.
- **Build system: Maven → Gradle 8.10** with Vanniktech maven-publish 0.30.0.
- **Release flow: semantic-release → tag-triggered Gradle publish.** A git tag matching `v[0-9]+.[0-9]+.[0-9]+` runs the release workflow.

### Added

- CI workflow (`build` + JaCoCo + Codecov).
- Docs site at https://ssrf-guard.devslab.kr/ (en + ko, mkdocs-material + i18n).
- Bilingual README (`README.md` / `README.ko.md`).
- Full test coverage replacing the placeholder `contextLoads()` — `NetUtilTest`, `SafeDnsResolverTest`, `SsrfGuardInterceptorTest`, `SsrfGuardAutoConfigurationTest`, `SsrfGuardIntegrationTest`.

Full migration notes in [docs/changelog.md](docs/changelog.md#200--rebrand-to-krdevslabssrf-guard).

## [1.1.0] — 2025-09-23

semantic-release rollup. Tagged but **never published to Maven Central**.

## [1.0.0] — 2025-09-23

Initial public release. Tagged but **never published to Maven Central**.

[Unreleased]: https://github.com/devslab-kr/ssrf-guard/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v3.0.0
[2.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v2.0.0
[1.1.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v1.1.0
[1.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v1.0.0
