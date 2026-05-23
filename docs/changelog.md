# Changelog

All notable changes to ssrf-guard are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [3.0.0] — Multi-module + LLM agent SSRF defense

The v2.0.0 starter was a single jar that only worked with Spring's `RestClient`. v3.0.0 splits the codebase along client boundaries, adds support for every common JVM HTTP stack, and ships a **Spring AI Tool wrapper** that closes the SSRF surface LLM agents have been introducing for the last two years.

### Added — new modules (opt-in)

| Module | Use case |
|---|---|
| `ssrf-guard-core` | Policy / NetUtil / Micrometer metrics interface — no Spring dependency |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 `DnsResolver` + `RedirectStrategy` (TOCTOU closure) |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` autoconfig (the v2.0.0 surface, now its own module) |
| `ssrf-guard-resttemplate` | **NEW** — Spring `RestTemplate` autoconfig for the enterprise/legacy crowd |
| `ssrf-guard-webclient` | **NEW** — Spring WebFlux `WebClient` `ExchangeFilterFunction` + autoconfig |
| `ssrf-guard-feign` | **NEW** — Spring Cloud OpenFeign `RequestInterceptor` + autoconfig |
| `ssrf-guard-springai` | **NEW** — Spring AI `ToolCallback` wrapper that validates URL-shaped tool arguments before LLM-driven execution. The hot SSRF surface in 2025+ |
| `ssrf-guard-jdkhttp` | **NEW** — `java.net.http.HttpClient` wrapper (no Spring, JDK 11+) |
| `ssrf-guard-okhttp` | **NEW** — OkHttp `Interceptor` + `Dns` (no Spring) |
| `ssrf-guard` | Meta artifact — bundles `-core` + `-httpclient5` + `-restclient` for v2.0.0 back-compat |

### Added — defense-in-depth hardening

- **IP-literal host rejection** (`ssrf.guard.reject-ip-literal-hosts=true` default). Any URL whose host parses as an IP literal in *any* form — dotted decimal (`127.0.0.1`), bare decimal (`2130706433`), hex (`0x7f000001`), octal (`0177.0.0.1`), partial (`127.1`), IPv6 (`[::1]`) — is rejected at the URL-time check, before DNS. Closes the obfuscated-IP bypass class.
- **Userinfo rejection** (`ssrf.guard.reject-user-info=true` default). URLs of the form `https://user:pass@host/...` are rejected — known SSRF bypass vector and credential-leak risk.
- **IPv4-mapped IPv6 + 6to4 unmapping**. `::ffff:10.0.0.5` and `2002:0a00::` (the 6to4 form wrapping 10.0.0.0/8) are now correctly classified as private, not "public IPv6 that happens to embed an internal v4". Java's `isLoopbackAddress()` misses these.

### Added — observability

- **Micrometer metrics**, auto-wired when a `MeterRegistry` bean is on the classpath:
  ```
  ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
  ssrf_guard_allowed_total{scheme="https"} 13042
  ```
- **Structured WARN logs** on every block: `ssrf-guard: <message> (reason=blocked_private_ip, scheme=http, host=169.254.169.254)`.

### Changed — BREAKING

- **Package renames.** Types moved out of the catch-all `kr.devslab.ssrfguard.security` package into their respective modules. The `ssrf-guard` meta artifact re-exports them, so `import kr.devslab.ssrfguard.*` consumers may need to update imports:

  | v2.0.0 | v3.0.0 |
  | --- | --- |
  | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardAutoConfiguration` | `kr.devslab.ssrfguard.restclient.SsrfGuardRestClientAutoConfiguration` |
  | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardProperties` | `kr.devslab.ssrfguard.core.SsrfGuardProperties` |
  | `kr.devslab.ssrfguard.security.SsrfGuardInterceptor` | `kr.devslab.ssrfguard.restclient.SsrfGuardClientHttpRequestInterceptor` |
  | `kr.devslab.ssrfguard.security.SafeDnsResolver` | `kr.devslab.ssrfguard.httpclient5.SafeDnsResolver` |
  | `kr.devslab.ssrfguard.security.SafeRedirectStrategy` | `kr.devslab.ssrfguard.httpclient5.SafeRedirectStrategy` |
  | `kr.devslab.ssrfguard.security.NetUtil` | `kr.devslab.ssrfguard.core.NetUtil` |
- **`SecurityException` → `SsrfGuardException`.** All rejection paths now throw `SsrfGuardException` (still a subclass of `SecurityException`, so v2.0.0 catch blocks keep working). The exception carries a `BlockReason` enum tag for metrics / logging.
- **New properties.** `ssrf.guard.reject-ip-literal-hosts` and `ssrf.guard.reject-user-info` default to `true` — turning them off restores v2.0.0 behaviour on those two checks.

### Migration

For most consumers, **update the version and rebuild** — that's it:

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.0.0</version>
</dependency>
```

The `ssrf-guard` meta artifact transitively pulls in `-core`, `-httpclient5`, and `-restclient`, which together provide the entire v2.0.0 surface.

If you use a different HTTP client, pick the matching module:

```xml
<!-- RestTemplate -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-resttemplate</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- WebClient -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-webclient</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Spring AI tool calls -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-springai</artifactId>
    <version>3.0.0</version>
</dependency>
```

If your code catches `SecurityException` from outbound calls, it still works — `SsrfGuardException extends SecurityException`. If you want the structured tag, catch `SsrfGuardException` and inspect `e.reason()`.

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
