# Installation

## Requirements

- **Java 21+**
- **Spring Boot 3.5+** (for the Spring-based modules)
- For Spring AI module: **Spring AI 1.0+**
- For Feign module: **Spring Cloud 2024.0+**

## Picking your module

ssrf-guard v3.0.0 is split along HTTP-client boundaries — pick the module(s) matching what you actually use. Most consumers want just one.

=== "RestClient (Spring Boot 3.x default)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    The meta artifact pulls in `-core`, `-httpclient5`, and `-restclient` — equivalent to the entire v2.0.0 surface.

=== "RestTemplate"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-resttemplate</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    Wires the same `ClientHttpRequestInterceptor` and `HttpComponentsClientHttpRequestFactory` onto Spring Boot's `RestTemplateBuilder`. Pull in `-restclient` too if you use both.

=== "WebClient (WebFlux)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-webclient</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    Wires an `ExchangeFilterFunction` onto the autoconfigured `WebClient.Builder`. Reactive — policy violations come back as `Mono.error(SsrfGuardException)`.

=== "Feign"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-feign</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    Registers a `feign.RequestInterceptor` — Spring Cloud OpenFeign auto-applies it to every `@FeignClient`.

=== "Spring AI tool calls"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-springai</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    Wraps every `ToolCallback` bean with URL-argument validation. The defining new SSRF surface — LLM agents that take a URL as a parameter and fetch it. v3.1+ delegates to `ssrf-guard-llm` (the framework-agnostic core); same public API.

=== "LangChain4j tool execution"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-langchain4j</artifactId>
        <version>3.1.0</version>
    </dependency>
    ```

    Same threat model as the Spring AI tab — different framework. Wraps every `ToolExecutor` bean. Useful when your LangChain4j tools are Spring beans (auto-wrapped by `BeanPostProcessor`); for programmatic / non-Spring use, the `SsrfGuardedToolExecutors.wrap(...)` helpers cover the `AiServices.builder(...).tools(Map<ToolSpecification, ToolExecutor>)` shape.

=== "Custom tool dispatcher (no framework)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-llm</artifactId>
        <version>3.1.0</version>
    </dependency>
    ```

    The framework-agnostic core. Use directly when you've built your own tool router (MCP server, internal RPC dispatcher, custom agent loop):

    ```java
    JsonToolInputGuard guard = new JsonToolInputGuard(urlPolicy);
    String violation = guard.checkOrFormatError(rawJsonInput);
    if (violation != null) return violation;   // structured JSON for the LLM
    // ... run the real tool
    ```

=== "Plain JDK HttpClient (no Spring)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-jdkhttp</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    Use directly:
    ```java
    HttpClient safe = new SsrfGuardedHttpClient(HttpClient.newHttpClient(), urlPolicy);
    ```

=== "OkHttp (no Spring)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-okhttp</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    ```java
    OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(new SsrfGuardOkHttpInterceptor(urlPolicy))
        .dns(new SsrfGuardOkHttpDns(hostPolicy, true))
        .build();
    ```

!!! tip "Latest version"
    Replace `3.0.0` with the latest from [Maven Central](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard).

!!! info "Coming from v2.0.0?"
    The `kr.devslab:ssrf-guard` coordinate still works — it's a meta artifact that pulls in `-core`, `-httpclient5`, and `-restclient`. Most consumers can upgrade by changing the version and rebuilding. Direct imports of `kr.devslab.ssrfguard.security.*` need to be updated — see the [v3.0.0 changelog](../changelog.md#300--multi-module--llm-agent-ssrf-defense) for the package-rename mapping.

## Minimal configuration

Whichever module you picked, the `ssrf.guard.*` keys are shared:

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true                    # default — set to false to opt out
    suffixes:                        # at minimum, this — empty means "block everything"
      - api.partner.com
      - example.org
```

Without any whitelist entries the guard is in **fail-closed** mode — every outbound call gets `Host not allowed`. That's deliberate.

## Optional hardening (defaults are sane)

```yaml
ssrf:
  guard:
    reject-ip-literal-hosts: true    # default — block http://127.0.0.1, http://2130706433, etc.
    reject-user-info: true           # default — block https://user:pass@host/...
    block-private-networks: true     # default — filter loopback / RFC-1918 / link-local / metadata IPs at DNS time
    follow-redirects: true           # default — and re-validate every hop
    allowed-schemes: [https]         # tighter than the [http, https] default
    allowed-ports: [443]             # tighter than [-1, 80, 443]
```

## Optional observability

Pulling in `spring-boot-starter-actuator` gets you Micrometer metrics for free:

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

The metric tags are bounded (`reason` is an enum, `scheme` is http/https) so Prometheus / Datadog / CloudWatch ingest happily.

## Verifying the install

For Spring Boot modules, the auto-config bean shows up in `--debug` output:

```bash
./gradlew bootRun --args='--debug' | grep -E 'SsrfGuard.*AutoConfiguration'
```

You should see at least one `matched:` line. Try a deliberately blocked URL — the response logs:

```
WARN  k.d.s.core.UrlPolicy : ssrf-guard: Host not allowed: evil.com (reason=blocked_host, scheme=https, host=evil.com)
```

Continue to the [Quickstart](quickstart.md) to see the guard in action against a real partner API, or [Security Model](../guides/security-model.md) for the full threat-model writeup.
