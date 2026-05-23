# Quickstart

A 5-minute walkthrough: install ssrf-guard, configure a whitelist, call a real partner API, and watch a malicious URL get blocked.

## 1. Add the dependency

```kotlin title="build.gradle.kts"
implementation("kr.devslab:ssrf-guard:2.0.0")
```

## 2. Configure the whitelist

```yaml title="application.yml"
ssrf:
  guard:
    suffixes:
      - api.partner.com
```

That's the only required setting — defaults handle scheme, port, private-network blocking, and redirect re-validation.

## 3. Inject `RestClient`

```java title="PartnerApiService.java"
package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PartnerApiService {

    private final RestClient client;

    public PartnerApiService(RestClient.Builder builder) {
        // The builder has already been customized by ssrf-guard — every
        // RestClient built from it gets the SSRF policy automatically.
        this.client = builder.build();
    }

    public String fetchCustomer(long id) {
        return client.get()
                .uri("https://api.partner.com/customers/{id}", id)
                .retrieve()
                .body(String.class);
    }
}
```

## 4. Try a blocked URL

Same `RestClient`, different host:

```java
String body = client.get()
        .uri("http://169.254.169.254/latest/meta-data/")
        .retrieve()
        .body(String.class);
```

```text
java.lang.SecurityException: Host not allowed: 169.254.169.254
    at kr.devslab.ssrfguard.security.SsrfGuardInterceptor.intercept(SsrfGuardInterceptor.java:48)
```

The interceptor rejected it before DNS, before the connection, before anything that could go wrong did. If you somehow bypass the interceptor (a URL form the parser handles differently), `SafeDnsResolver` catches it at DNS time and refuses to return the loopback / link-local IP.

## 5. Try a redirect attack

Add a host you control that returns `302 Location: http://169.254.169.254/...`:

```yaml title="application.yml"
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - your-redirect-host.com
```

```java
client.get()
        .uri("https://your-redirect-host.com/redirect-to-metadata")
        .retrieve()
        .body(String.class);
```

```text
org.apache.hc.client5.http.RedirectException: Blocked redirect to host: 169.254.169.254 cause: ...
```

`SafeRedirectStrategy` ran the redirect target through the same DNS resolver that blocks link-local addresses. The attacker's bait worked exactly as well as a direct call to `169.254.169.254` — i.e., not at all.

## What just happened

```
RestClient.get(uri)
   ↓
SsrfGuardInterceptor   ← scheme / host / port whitelist (no DNS yet)
   ↓
HttpComponents → SafeDnsResolver   ← whitelist + private-IP filter
   ↓
Socket.connect(resolved-addr)   ← same InetAddress array, no second lookup
   ↓
(on 3xx)
SafeRedirectStrategy   ← run target URI through the same checks
   ↓
loop or terminate
```

Three independent gates, each closing a different bypass an attacker would try.

## Where to go next

- [Security Model](../guides/security-model.md) — what each layer guarantees and where it doesn't
- [Configuration](../guides/configuration.md) — every property, defaults, when to change them
- [Changelog](../changelog.md) — v2.0.0 migration mapping from the legacy `com.devs.lab` coordinate
