# Installation

## Requirements

- **Java 21+**
- **Spring Boot 3.5+**

## Adding the dependency

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard</artifactId>
        <version>2.0.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:ssrf-guard:2.0.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:ssrf-guard:2.0.0'
    }
    ```

!!! tip "Latest version"
    Replace `2.0.0` with the latest from [Maven Central](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard).

!!! info "Coming from 1.x?"
    The legacy `com.devs.lab:ssrf-guard-spring-boot-starter` was never published to Maven Central. v2.0.0 is the first proper release on `kr.devslab:ssrf-guard`. Migration mapping in the [v2.0.0 changelog](../changelog.md).

## What ssrf-guard pulls in

- `spring-boot-starter` (transitive)
- `spring-boot-autoconfigure`
- `org.apache.httpcomponents.client5:httpclient5` — the HTTP stack that backs the wrapped `RestClient`

Spring Web (`spring-boot-starter-web`) is **optional** at compile time but required at runtime — the auto-configuration is gated by `@ConditionalOnClass(RestClient.class)`. Pure-WebFlux applications that don't use `RestClient` at all simply don't get the guard activated.

## What you bring yourself

Nothing. ssrf-guard ships an auto-configuration that pins itself onto Spring Boot's stock `RestClient.Builder`, so every `RestClient` your code constructs picks up the SSRF policy automatically.

You may want to configure these in `application.yml`:

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true                # default — set to false to opt out completely
    suffixes:                    # at minimum, this — empty means "block everything"
      - api.partner.com
      - example.org
```

A consumer with no `ssrf.guard.*` configuration at all gets the guard active with **no hosts whitelisted**, which means every outbound call fails with `Host not allowed`. That's deliberate (fail-closed) — but it's worth knowing.

## What auto-configuration does

When `ssrf.guard.enabled` is `true` (the default) and `RestClient` is on the classpath, `SsrfGuardAutoConfiguration` activates and registers:

- `SafeDnsResolver` — applies the whitelist again at DNS time, filters out private/loopback/link-local/multicast IPs
- `CloseableHttpClient` — built off Apache HttpClient 5, with the resolver wired in and (when redirects are enabled) `SafeRedirectStrategy` re-validating every hop
- `HttpComponentsClientHttpRequestFactory` — with the configured connect/read timeouts
- `SsrfGuardInterceptor` — the front-line scheme/host/port check applied before DNS
- `ssrfRestClientCustomizer` — a `RestClientCustomizer` that pins the factory + interceptor onto Spring Boot's auto-configured `RestClient.Builder`

Every bean uses `@ConditionalOnMissingBean`. Define your own to override any piece.

## Verifying the install

Start the app, then check the auto-config bean is in the context:

```bash
./gradlew bootRun --args='--debug' | grep SsrfGuardAutoConfiguration
```

You should see a line like `SsrfGuardAutoConfiguration matched: ...`. Try a deliberately blocked URL and you'll see `SecurityException: Host not allowed: ...` in the log.

Continue to the [Quickstart](quickstart.md) to see the guard in action against a real partner API.
