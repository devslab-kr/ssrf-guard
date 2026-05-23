# ssrf-guard

**English** · [한국어](README.ko.md)

> SSRF (Server-Side Request Forgery) protection for the JVM — whitelist-based outbound HTTP guard with private-network blocking, redirect validation, TOCTOU mitigation, and **Spring AI tool URL validation** to close the LLM-agent SSRF surface.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/ssrf-guard.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard)
[![CI](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/ssrf-guard/branch/main/graph/badge.svg)](https://codecov.io/gh/devslab-kr/ssrf-guard)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[Documentation → ssrf-guard.devslab.kr](https://ssrf-guard.devslab.kr/)**

> 💬 Questions, ideas, sharing your application? Head to [**devslab-examples Discussions**](https://github.com/devslab-kr/devslab-examples/discussions) — bilingual, maintained by the same folks who write the libraries.

## Runnable examples

Standalone Spring Boot projects that exercise every module documented below — clone, `./gradlew bootRun`, curl. No copy-paste; the examples are wired end-to-end (smoke tests included).

| Demo | Showcases |
| --- | --- |
| [`ssrf-guard-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-demo) | RestClient + RestTemplate + WebClient all wired through one `UrlPolicy`. 15-pattern attack matrix endpoint, Micrometer metrics |
| [`ssrf-guard-springai-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-springai-demo) | ⭐ LLM agent SSRF defense. Fake-LLM driver, no API key needed |
| [`ssrf-guard-feign-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-feign-demo) | Spring Cloud OpenFeign `RequestInterceptor` integration |
| [`ssrf-guard-jdkhttp-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-jdkhttp-demo) | `java.net.http.HttpClient` wrapper — no Spring dependency on the library itself |
| [`ssrf-guard-okhttp-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-okhttp-demo) | OkHttp `Interceptor` + `Dns` integration — also no Spring |

Full index at [github.com/devslab-kr/devslab-examples](https://github.com/devslab-kr/devslab-examples).

## Module matrix

Pick the module matching your HTTP client. The core (`ssrf-guard-core`) follows transitively.

| Module | Use case | Spring? |
|---|---|---|
| **`ssrf-guard`** | Meta artifact — RestClient + HttpClient5 (v2.0.0 back-compat) | ✅ |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` | ✅ |
| `ssrf-guard-resttemplate` | Spring `RestTemplate` | ✅ |
| `ssrf-guard-webclient` | Spring WebFlux `WebClient` — URL-time filter + reactor-netty DNS-time IP filter (v3.1+) | ✅ |
| `ssrf-guard-feign` | Spring Cloud OpenFeign | ✅ |
| `ssrf-guard-llm` 🧩 | Framework-agnostic JSON tool-input validator (v3.1+) — reused by the LLM adapters | — |
| **`ssrf-guard-springai`** ⭐ | Spring AI `ToolCallback` URL validation — thin adapter over `-llm` | ✅ |
| **`ssrf-guard-langchain4j`** ⭐ | LangChain4j `ToolExecutor` URL validation — same defense for the other Java LLM framework (v3.1+) | ✅ |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 directly | — |
| `ssrf-guard-jdkhttp` | `java.net.http.HttpClient` | — |
| `ssrf-guard-okhttp` | OkHttp | — |

## What it does

Every outbound HTTP call from your service runs through a four-layer SSRF filter before a socket is ever opened:

1. **URL-time check (front line)** — scheme / host / port / IP-literal-form / userinfo rejected at the cheapest gate, before any DNS lookup. Catches the obfuscated-IP bypass class (`http://2130706433/` → `127.0.0.1`).
2. **DNS-time whitelist re-check** — same host policy applied a second time when the hostname is resolved.
3. **Private-network IP filter** — loopback, RFC-1918, link-local (incl. AWS metadata at `169.254.169.254`), CGNAT, IPv6 ULA, **IPv4-mapped IPv6 + 6to4 unmapping** (`::ffff:10.0.0.5` and `2002:0a00::` correctly classified as private).
4. **Redirect re-validation** — every 3xx hop runs through the same checks. An attacker can't whitelist `example.com` and then redirect to `169.254.169.254`.

The same `InetAddress` array the resolver validated is what HttpClient hands to `Socket.connect()` — TOCTOU window closed.

## Spring AI tool calls — the new SSRF surface

LLM agents that take URLs as tool arguments are SSRF vectors by default:

```java
@Tool("Fetch a URL")
String fetchUrl(String url) {
    return restClient.get().uri(url).retrieve().body(String.class);
    //          ↑ attacker controls the URL — one-line SSRF
}
```

`ssrf-guard-springai` wraps every `ToolCallback` so URL-shaped arguments are validated against the policy before the tool runs, and on rejection returns a structured error string the LLM can interpret and recover from.

```java
ToolCallback[] raw = ToolCallbacks.from(new MyTools());
ToolCallback[] safe = SsrfGuardedToolCallbacks.wrap(raw, urlPolicy);
```

Auto-config picks it up — any `@Bean ToolCallback` gets wrapped via a `BeanPostProcessor`.

## Install

### Maven

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("kr.devslab:ssrf-guard:3.0.0")
```

> **Upgrading from v2.0.0?** The meta `kr.devslab:ssrf-guard:3.0.0` keeps the v2.0.0 API working — pulls in `-core`, `-httpclient5`, `-restclient` transitively. Direct imports of `kr.devslab.ssrfguard.security.*` need updates — see the [v3.0.0 changelog](CHANGELOG.md#300--multi-module--llm-agent-ssrf-defense) for the package-rename mapping.

## Configuration

```yaml
ssrf:
  guard:
    enabled: true                          # master switch
    allowed-schemes: [ "http", "https" ]
    allowed-ports:  [ -1, 80, 443 ]        # -1 = default port for the scheme
    block-private-networks: true
    reject-ip-literal-hosts: true          # NEW v3.0.0 — block http://127.0.0.1, http://2130706433, etc.
    reject-user-info: true                 # NEW v3.0.0 — block https://user:pass@host/...
    follow-redirects: true

    # Exact-match whitelist
    exact-hosts:
      - api.partner.com
      - billing.example.org

    # Suffix whitelist — `partner.com` covers `partner.com` AND any subdomain
    # of it, but not `badpartner.com` (label-boundary match).
    suffixes:
      - partner.com
      - example.org

    connect-timeout: 5s
    read-timeout: 10s
```

Once the starter is on the classpath every `RestClient` Spring Boot builds for you automatically picks up the policy — no extra wiring on the consumer side.

## Usage

```java
@Service
public class PartnerApi {

    private final RestClient client;

    public PartnerApi(RestClient.Builder builder) {
        this.client = builder.build();
    }

    public Customer fetch(long id) {
        // Whitelisted host → goes through. Anything not on the list throws
        // SecurityException before the connection is opened.
        return client.get()
                .uri("https://api.partner.com/customers/{id}", id)
                .retrieve()
                .body(Customer.class);
    }
}
```

What happens when the request isn't whitelisted:

```text
kr.devslab.ssrfguard.core.SsrfGuardException: Host not allowed: evil.com
    (reason=blocked_host, scheme=https, host=evil.com)
    at kr.devslab.ssrfguard.core.UrlPolicy.reject(...)
```

`SsrfGuardException extends SecurityException` — v2.0.0 `catch (SecurityException e)` code keeps working. Catch the new type to read `e.reason()` (a `BlockReason` enum: `blocked_host`, `blocked_private_ip`, `blocked_ip_literal`, `blocked_userinfo`, `blocked_scheme`, `blocked_port`, `blocked_redirect`).

## Observability (auto-wired with Micrometer)

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

Plus a structured WARN log on every block:

```
WARN k.d.s.core.UrlPolicy : ssrf-guard: Host not allowed: evil.com (reason=blocked_host, scheme=https, host=evil.com)
```

Tags are bounded (`reason` is an enum, `scheme` is http/https) — Prometheus / Datadog / CloudWatch ingest happily.

## What auto-configuration registers (RestClient module)

When `ssrf.guard.enabled=true` (the default), the RestClient autoconfig activates and registers:

- `SafeDnsResolver` — whitelist + private-IP filter, plugged into Apache HttpClient 5's connection manager
- `CloseableHttpClient` — built with the resolver wired in and (when redirects are enabled) a `SafeRedirectStrategy`
- `HttpComponentsClientHttpRequestFactory` — with configured connect/read timeouts
- `UrlPolicy` — the front-line URL-time gate (scheme, host, port, IP-literal, userinfo)
- `SsrfGuardClientHttpRequestInterceptor` — Spring `ClientHttpRequestInterceptor` that delegates to the policy
- `SsrfGuardMetrics` — Micrometer-backed when a `MeterRegistry` is present, no-op otherwise
- `RestClientCustomizer` (named `ssrfRestClientCustomizer`) — pins the factory + interceptor onto Spring Boot's auto-built `RestClient.Builder`

Each module has its own auto-config — `SsrfGuardRestTemplateAutoConfiguration`, `SsrfGuardWebClientAutoConfiguration`, `SsrfGuardFeignAutoConfiguration`, `SsrfGuardSpringAiAutoConfiguration`. They all reuse the same `UrlPolicy` and `SsrfGuardMetrics` beans. Every bean is `@ConditionalOnMissingBean`, so you can swap any piece.

## Requirements

- Java 21+
- Spring Boot 3.5+ (for Spring-based modules)
- Spring AI 1.0+ (for the `springai` module)
- Spring Cloud 2024.0+ (for the `feign` module)
- Apache HttpClient 5 (pulled in transitively by `-httpclient5`, `-restclient`, `-resttemplate`)

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

Built by [Devslab](https://devslab.kr) · Part of the [DevsLab open-source toolkit](https://github.com/devslab-kr).
