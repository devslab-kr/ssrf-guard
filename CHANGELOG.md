# Changelog

The full changelog with anchored links is published at
**<https://ssrf-guard.devslab.kr/changelog/>** ([한국어](https://ssrf-guard.devslab.kr/ko/changelog/)).

The source of truth for the entries below is [docs/changelog.md](docs/changelog.md) — please update there.

## [Unreleased]

## [3.2.0] — scanEmbedded: mid-sentence URL scanning for the LLM tool-input guard

### Added

- **`scanEmbedded` — mid-sentence URL scanning for the LLM tool-input guard.** Opt-in via a new `JsonToolInputGuard` constructor argument or `ssrf.guard.llm.scan-embedded` (default `false`): URLs buried mid-sentence inside tool-input strings (`"summarize http://169.254.169.254/ please"`) are extracted and validated. Ported from `@devslab/ssrf-guard-js` 0.5.0. Default behavior unchanged.

### Security

- **The LLM tool-input scanner no longer drops non-`http(s)` schemes and protocol-relative URLs at the collection stage.** `file://`, `ftp://`, `gopher://` and any other `scheme://` URL in tool input were filtered out *before* the policy ever saw them, so they passed the guard in silence rather than being rejected by `allowedSchemes`. Protocol-relative references (`//evil.example/x`), which inherit the caller's scheme at fetch time and are therefore real fetch targets, were not collected at all.

  Collection is now generous and the **policy decides** — the same correction made on the JS side in `@devslab/ssrf-guard-js` 0.2.0. `file://` and friends now yield `blocked_scheme`; `//host` is validated against the host allowlist as if it resolved to https. Schemes with no authority (`mailto:`, `urn:`, `data:`) are still ignored: they have no host to check and are not fetch surfaces.

  This is the same shape as the uppercase-scheme bypass fixed in 3.1.1 — a filter at the collection stage letting a URL skip validation entirely — and was found by the first JVM ↔ JS parity audit. Affects `ssrf-guard-springai` and `ssrf-guard-langchain4j`.

- **Tool-input URLs that `java.net.URI` cannot parse no longer skip validation.** Whole-string URLs with surrounding whitespace are trimmed before parsing, and URLs whose path contains `URI`-illegal characters (`/a[0]`) are re-validated on `scheme://authority` alone instead of being silently skipped. Affects `ssrf-guard-springai` and `ssrf-guard-langchain4j`.

### Migration

Drop in v3.2.0 — no consumer code changes; `scanEmbedded` is off by default. Full notes in [docs/changelog.md](docs/changelog.md#320--scanembedded-mid-sentence-url-scanning-for-the-llm-tool-input-guard).

## [3.1.1] — Fix uppercase-scheme bypass in the LLM tool-input guard

### Security

- **Uppercase-scheme bypass in the LLM tool-input guard fixed.** `JsonToolInputGuard` collected candidate URLs with a case-sensitive `http://` / `https://` prefix test, so an uppercase-scheme tool-input URL (`HTTP://169.254.169.254/…`) was never collected and skipped policy validation entirely — bypassing the host allowlist and IP-literal checks. Detection is now case-insensitive. Affects `ssrf-guard-springai` and `ssrf-guard-langchain4j`.

### Migration

Drop in v3.1.1 — no consumer code changes. If your app exposes an LLM tool that
takes a URL and you rely on `ssrf-guard-springai` / `ssrf-guard-langchain4j` to
screen tool input, upgrade to pick up the fix.

## [3.1.0] — LLM core extraction, LangChain4j, WebClient DNS gap, GraalVM hints

### Added

- New module **`ssrf-guard-llm`** — framework-agnostic JSON tool-input validator (`ToolInputGuard` + `JsonToolInputGuard`). Holds the URL detection + policy validation logic the adapter modules share.
- New module **`ssrf-guard-langchain4j`** — wraps LangChain4j `ToolExecutor`. Same defense as `-springai` for the other major Java LLM framework. Spring auto-wrap via `BeanPostProcessor`; `SsrfGuardedToolExecutors.wrap(...)` for non-Spring.
- **GraalVM native-image hints** via `META-INF/spring/aot.factories` in `-llm`. Adapter modules get free AOT support from Spring Boot 3.

### Changed

- `ssrf-guard-springai` refactored to a ~30-line thin adapter over `-llm`. Public API unchanged.
- Error payload backed by typed `SsrfBlockPayload` record (was `Map.of`). Same JSON wire shape; better AOT introspection.

### Fixed

- **WebClient DNS-time defense gap closed.** `SsrfGuardReactorAddressResolverGroup` filters reactor-netty's resolved IPs against the private/loopback ranges — closes the v3.0.x DNS-rebinding window WebFlux apps had.

Full notes in [docs/changelog.md](docs/changelog.md#310--llm-core-extraction-langchain4j-webclient-dns-gap-graalvm-hints).

## [3.0.1] — Fix metrics bean classpath gate

### Fixed

- `ClassNotFoundException: io.micrometer.core.instrument.MeterRegistry` when consumers don't have `micrometer-core` on the classpath. The metrics bean factory method declared `ObjectProvider<MeterRegistry>` as a parameter, and the JVM resolves parameter types at class load time even when `ObjectProvider` would handle the missing bean. Fixed by moving the Micrometer-backed metrics bean into a static inner `@Configuration` class gated by `@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")`, with a `NoOp` fallback in the outer autoconfig. Affected: `-restclient`, `-resttemplate` (via `-restclient`), `-webclient`, `-feign`, `-springai`. Full notes in [docs/changelog.md](docs/changelog.md#301--fix-metrics-bean-classpath-gate).

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

[Unreleased]: https://github.com/devslab-kr/ssrf-guard/compare/v3.1.0...HEAD
[3.1.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v3.1.0
[3.0.1]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v3.0.1
[3.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v3.0.0
[2.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v2.0.0
[1.1.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v1.1.0
[1.0.0]: https://github.com/devslab-kr/ssrf-guard/releases/tag/v1.0.0
