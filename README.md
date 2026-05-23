# ssrf-guard

**English** · [한국어](README.ko.md)

> SSRF (Server-Side Request Forgery) protection for Spring Boot — whitelist-based outbound HTTP guard with private-network blocking, redirect validation, and TOCTOU mitigation.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/ssrf-guard.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard)
[![CI](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/ssrf-guard/branch/main/graph/badge.svg)](https://codecov.io/gh/devslab-kr/ssrf-guard)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[Documentation → ssrf-guard.devslab.kr](https://ssrf-guard.devslab.kr/)**

## What it does

Every outbound HTTP call from your Spring Boot service runs through a four-layer SSRF filter before a socket is ever opened:

1. **Scheme / host / port** — string-level interceptor rejects anything outside the configured allow-lists, no DNS yet.
2. **Whitelist re-check at DNS time** — same host policy applied a second time when the hostname is resolved, closing the gap a forged URL might have slipped through.
3. **Private-network IP filter** — loopback (`127.0.0.0/8`, `::1`), RFC-1918 (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`, `fe80::/10`, including the AWS metadata endpoint), CGNAT (`100.64/10`), and IPv6 ULA (`fc00::/7`) are all blocked.
4. **Redirect re-validation** — every 3xx hop runs through the same scheme/host/IP rules. An attacker can't whitelist `example.com` and then redirect to `169.254.169.254`.

The same `InetAddress` array the resolver validated is what HttpClient hands to `Socket.connect()` — TOCTOU window closed.

## Install

### Maven

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("kr.devslab:ssrf-guard:2.0.0")
```

> **Upgrading from `com.devs.lab:ssrf-guard-spring-boot-starter` 1.x?** The coordinate, package, and minimum Spring Boot version all changed in v2.0.0 — see the [v2.0.0 changelog](CHANGELOG.md#200--rebrand-to-krdevslabssrf-guard).

## Configuration

```yaml
ssrf:
  guard:
    enabled: true                          # master switch
    allowed-schemes: [ "http", "https" ]
    allowed-ports:  [ -1, 80, 443 ]        # -1 = default port for the scheme
    block-private-networks: true
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
java.lang.SecurityException: Host not allowed: evil.com
    at kr.devslab.ssrfguard.security.SsrfGuardInterceptor.intercept(...)
```

## What auto-configuration registers

When `ssrf.guard.enabled=true` (the default), `SsrfGuardAutoConfiguration` activates and registers:

- `SafeDnsResolver` — whitelist + private-IP filter, plugged into Apache HttpClient 5's connection manager
- `CloseableHttpClient` — built with the resolver wired in and (when redirects are enabled) a `SafeRedirectStrategy`
- `HttpComponentsClientHttpRequestFactory` — with configured connect/read timeouts
- `SsrfGuardInterceptor` — front-line scheme/host/port check
- `RestClientCustomizer` (named `ssrfRestClientCustomizer`) — pins the factory + interceptor onto Spring Boot's auto-built `RestClient.Builder`

Every bean is `@ConditionalOnMissingBean`, so you can swap any piece for your own implementation (e.g., provide a `CloseableHttpClient` with your auth headers and keep the rest of the SSRF policy intact).

## Requirements

- Java 21+
- Spring Boot 3.5+
- Apache HttpClient 5 (pulled in transitively)

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

Built by [Devslab](https://devslab.kr) · Part of the [DevsLab open-source toolkit](https://github.com/devslab-kr).
