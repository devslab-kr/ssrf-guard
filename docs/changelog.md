# Changelog

All notable changes to ssrf-guard are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [2.0.0] — Rebrand to `kr.devslab:ssrf-guard`

### Changed

- **BREAKING — coordinate changed.** From `com.devs.lab:ssrf-guard-spring-boot-starter` to `kr.devslab:ssrf-guard`. The legacy artifact was never published to Maven Central, so v2.0.0 is the first proper Central release.
- **BREAKING — package renamed.** `devs.lab.ssrf.*` → `kr.devslab.ssrfguard.*`:

  | Old | New |
  | --- | --- |
  | `devs.lab.ssrf.config.SsrfGuardAutoConfiguration` | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardAutoConfiguration` |
  | `devs.lab.ssrf.security.SsrfGuardProperties` | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardProperties` |
  | `devs.lab.ssrf.security.SsrfGuardInterceptor` | `kr.devslab.ssrfguard.security.SsrfGuardInterceptor` |
  | `devs.lab.ssrf.security.SafeDnsResolver` | `kr.devslab.ssrfguard.security.SafeDnsResolver` |
  | `devs.lab.ssrf.security.SafeRedirectStrategy` | `kr.devslab.ssrfguard.security.SafeRedirectStrategy` |
  | `devs.lab.ssrf.security.NetUtil` | `kr.devslab.ssrfguard.security.NetUtil` |

- **BREAKING — `SsrfGuardApplication` removed.** The empty `@SpringBootApplication` was vestigial scaffolding from the original Spring Initializr template; a starter library has no business carrying a main class.
- **Build system: Maven → Gradle 8.10** with Vanniktech maven-publish 0.30.0. Same convention as easy-paging-spring-boot-starter and api-log.
- **Release flow: semantic-release → tag-triggered Gradle publish.** A git tag matching `v[0-9]+.[0-9]+.[0-9]+` runs the release workflow, which builds + signs + uploads to Sonatype Central Portal and creates a GitHub Release in one step.

### Added

- **CI workflow** (`.github/workflows/ci.yml`) — runs `./gradlew build jacocoTestReport` on every push to `main` and on every PR, uploads coverage to Codecov.
- **Docs site** at https://ssrf-guard.devslab.kr/ — installation, quickstart, security model, configuration reference. Built with mkdocs-material + i18n (English + Korean).
- **Bilingual README** (`README.md` / `README.ko.md`).
- **Full test coverage** of every documented defense:
  - `NetUtilTest` — whitelist matching (exact + suffix), IDN normalisation, private-IP classification across IPv4 (loopback, RFC-1918, link-local incl. AWS metadata, CGNAT, benchmark, broadcast) and IPv6 (ULA, link-local).
  - `SafeDnsResolverTest` — whitelist gate + private-IP filter, including the "filtered everything" path.
  - `SsrfGuardInterceptorTest` — scheme/host/port accept/reject matrix, suffix label-boundary lookalike (the classic `badexample.com` bypass).
  - `SsrfGuardAutoConfigurationTest` — every public bean of the auto-config is registered when enabled, and none are when `ssrf.guard.enabled=false`.
  - `SsrfGuardIntegrationTest` — real HTTP through `MockWebServer`, end-to-end through the four-layer defense.

### Migration

Update your dependency coordinate and any direct imports:

```xml
<!-- v1.x (never on Maven Central) -->
<dependency>
    <groupId>com.devs.lab</groupId>
    <artifactId>ssrf-guard-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- v2.0.0 -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

`application.yml` keys are unchanged — `ssrf.guard.*` works identically.

Direct imports of the security types? Replace `devs.lab.ssrf` with `kr.devslab.ssrfguard` and split between `kr.devslab.ssrfguard.autoconfigure` (properties + auto-config) and `kr.devslab.ssrfguard.security` (interceptor + resolver + redirect + NetUtil).

## [1.1.0] — 2025-09-23

semantic-release rollup of pre-v2 work. Tagged but **never published to Maven Central**.

- README + releaser templates touched up

## [1.0.0] — 2025-09-23

Initial public release. Tagged but **never published to Maven Central**.

- First cut of the SSRF starter under `com.devs.lab:ssrf-guard-spring-boot-starter`
