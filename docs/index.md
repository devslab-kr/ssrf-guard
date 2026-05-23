# ssrf-guard

> SSRF (Server-Side Request Forgery) protection for the JVM — whitelist-based outbound HTTP guard with private-network blocking, redirect validation, TOCTOU mitigation, and **LLM agent tool URL validation** for Spring AI.

## Why

A JVM service that lets users supply URLs — webhook callbacks, image-fetch helpers, "import from URL," **an LLM agent's `fetch_url` tool**, anything taking a string and turning it into an HTTP call — is one fetch away from leaking AWS credentials via `http://169.254.169.254/latest/meta-data/`, scanning an internal subnet, or invoking an admin endpoint exposed only on the loopback. SSRF was [#10 on the OWASP Top 10 2021](https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/) for exactly this kind of "default trust your own server" failure — and the LLM-agent boom of 2024-2025 has made it more relevant than ever.

ssrf-guard ships a small core (policy + IP classification) plus per-client modules that drop into whatever HTTP stack you already use.

## Module matrix

Pick the module(s) matching your HTTP client. The core is pulled in transitively.

| Module | What it wraps | Spring needed? |
|---|---|---|
| **`ssrf-guard`** | Meta artifact — RestClient + HttpClient5 (v2.0.0 back-compat) | Yes |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` | Yes |
| `ssrf-guard-resttemplate` | Spring `RestTemplate` (enterprise / legacy) | Yes |
| `ssrf-guard-webclient` | Spring WebFlux `WebClient` — URL-time filter **and** reactor-netty DNS-time IP filter (v3.1+) | Yes (WebFlux) |
| `ssrf-guard-feign` | Spring Cloud OpenFeign | Yes (Cloud) |
| `ssrf-guard-llm` 🧩 | Framework-agnostic JSON tool-input validator (v3.1+). Used by the springai / langchain4j adapters; usable directly from a custom dispatcher. | No |
| **`ssrf-guard-springai`** ⭐ | Spring AI `ToolCallback` — closes the LLM-agent SSRF surface (thin adapter over `-llm`) | Yes (AI) |
| **`ssrf-guard-langchain4j`** ⭐ | LangChain4j `ToolExecutor` — same defense for the other Java LLM framework (v3.1+, thin adapter over `-llm`) | Yes |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 directly | No |
| `ssrf-guard-jdkhttp` | `java.net.http.HttpClient` (JDK 11+) | No |
| `ssrf-guard-okhttp` | OkHttp | No |

## The defenses

1. **URL-time check (front line)** — scheme + host + port + IP-literal-form + userinfo, all before any DNS lookup. Catches the obfuscated-IP bypass class (`http://2130706433/` → `127.0.0.1`) at the cheapest possible gate.
2. **DNS-time whitelist re-check** — same host policy applied a second time when the hostname is actually resolved.
3. **Private-network IP filter** — loopback, RFC-1918, link-local (incl. AWS metadata at `169.254.169.254`), CGNAT, IPv6 ULA, IPv4-mapped IPv6 (the `::ffff:10.0.0.5` bypass), 6to4 (`2002::/16`) — all blocked.
4. **Redirect re-validation** — every 3xx hop is run through the same checks. An attacker can't whitelist `example.com` and then 302 you to `169.254.169.254`.

The `InetAddress` array the DNS resolver validates is the *exact same* array Apache HttpClient hands to `Socket.connect()` — there's no second DNS lookup between validation and connection. That closes the TOCTOU window a naïve whitelist would otherwise leave open.

## Spring AI tool calls — the new SSRF surface

LLM agents fetch URLs as a routine tool call:

```python
@tool
def fetch_url(url: str) -> str:
    return requests.get(url).text    # ← one-line SSRF if URL is attacker-controlled
```

The Java/Spring AI equivalent is *exactly* as vulnerable. `ssrf-guard-springai` wraps any `ToolCallback` so URL-shaped arguments are validated against the policy before the underlying tool runs — and on rejection, returns a structured error string the LLM can interpret and recover from rather than an opaque exception.

```java
ToolCallback[] raw = ToolCallbacks.from(new MyTools());
ToolCallback[] safe = SsrfGuardedToolCallbacks.wrap(raw, urlPolicy);

ChatClient.create(chatModel)
    .prompt("Summarise the homepage of example.com")
    .toolCallbacks(safe)
    .call().content();
```

The auto-config picks this up automatically when `ssrf-guard-springai` is on the classpath — any `@Bean ToolCallback` is wrapped via a `BeanPostProcessor`.

## Observability

Out of the box, when a `MeterRegistry` is on the classpath:

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

Plus a structured WARN log on every block:

```
WARN ssrf-guard: blocked DNS — all resolved IPs are private/loopback
       (host=evil.com, resolved=[evil.com/10.0.0.5])
```

## Quick install

For Spring Boot 3.5+ with `RestClient`:

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.0.0</version>
</dependency>
```

```yaml
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - example.org
```

Continue to the [Installation guide](getting-started/installation.md) for the full module matrix, or jump to the [Quickstart](getting-started/quickstart.md) for a 5-minute walkthrough.

## Where it lives

- **GitHub**: <https://github.com/devslab-kr/ssrf-guard>
- **Maven Central**: <https://central.sonatype.com/artifact/kr.devslab/ssrf-guard>
- **Docs**: <https://ssrf-guard.devslab.kr/>

## License

Apache License 2.0. Part of the [DevsLab open-source toolkit](https://github.com/devslab-kr).
