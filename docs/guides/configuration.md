# Configuration

Every option under `ssrf.guard.*`, with defaults and when to change them.

## Master switch

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.enabled` | `true` | Set to `false` to disable the entire starter. No beans registered; Spring Boot's stock `RestClient.Builder` handles outbound traffic unchanged. |

## Whitelist

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.exact-hosts` | `[]` | Exact-match host whitelist. Comparison is case-insensitive and IDN-normalized. |
| `ssrf.guard.suffixes` | `[]` | Suffix-match whitelist. `example.com` matches itself **and** any subdomain (`api.example.com`, `a.b.example.com`) on a label boundary — `badexample.com` does NOT match. |

Both lists are empty by default. Empty whitelists mean **every outbound call is rejected with `Host not allowed`** — fail-closed. You must set at least one entry for the guard to permit anything.

```yaml
ssrf:
  guard:
    exact-hosts:
      - api.partner.com         # only that exact name
    suffixes:
      - partner-internal.com    # partner-internal.com and all its subdomains
```

## Scheme / port

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.allowed-schemes` | `["http", "https"]` | URL schemes the guard will permit. Set to `["https"]` to forbid plain HTTP. |
| `ssrf.guard.allowed-ports` | `[-1, 80, 443]` | TCP ports the guard will permit. **`-1` means "URI omitted the explicit port, default for the scheme applies"** — leaving it in is what permits the common `https://api.example.com/` (no explicit `:443`) form. |

```yaml
ssrf:
  guard:
    allowed-schemes: [ "https" ]    # https only
    allowed-ports:   [ -1, 443 ]    # default port or explicit 443
```

## Private-network blocking

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.block-private-networks` | `true` | When `true`, every IP the DNS resolver returns must be publicly routable — loopback, RFC-1918, link-local, CGNAT, multicast, broadcast, IPv6 ULA all blocked. See [Security model](security-model.md#what-block-private-networks-blocks) for the full list. |

Turn off only if you intentionally need internal calls (e.g., service-to-service over a private subnet). It's the single most common config knob to accidentally re-enable SSRF — review every change that flips it.

## Redirect handling

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.follow-redirects` | `true` | When `true`, every 3xx hop is re-validated through the same scheme + DNS-resolver policy. When `false`, the client returns the redirect to the caller as-is and the request stops there. |

Disabling redirects is sometimes used as belt-and-suspenders security but it tends to break partner integrations that depend on `301 Moved Permanently` for HTTP→HTTPS upgrade.

## Timeouts

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.connect-timeout` | `5s` | Socket connect timeout. Standard Spring `Duration` parsing (`5s`, `500ms`, `PT5S`, etc.). |
| `ssrf.guard.read-timeout` | `10s` | Socket read timeout. |

The defaults are intentionally conservative — slow partner APIs can be a denial-of-service vector if the timeouts are unbounded. Tune up only as needed.

## Reserved

| Property | Default | Notes |
|---|---|---|
| `ssrf.guard.enable-additional-dns-caching` | `false` | Reserved for future use. Has no effect today — every request resolves DNS through `SafeDnsResolver` and the JVM-wide `networkaddress.cache.ttl` controls caching. |

## A complete example

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true
    allowed-schemes: [ "https" ]
    allowed-ports: [ -1, 443 ]
    block-private-networks: true
    follow-redirects: true
    connect-timeout: 5s
    read-timeout: 10s
    exact-hosts:
      - api.stripe.com
      - hooks.slack.com
    suffixes:
      - partner.example.com
      - googleapis.com
```

A whitelist this size will keep almost any partner-integration-heavy service safe from SSRF.

## LLM-adapter properties (v3.1+)

Two additional toggles control automatic wrapping of LLM tool beans. Both default to `true` — set to `false` to wrap selectively from your own code (via `SsrfGuardedToolCallbacks.wrap(...)` or `SsrfGuardedToolExecutors.wrap(...)`) without the `BeanPostProcessor` running.

| Key | Default | Effect |
|---|---|---|
| `ssrf.guard.springai.wrap-tool-callbacks` | `true` | Wraps every Spring AI `ToolCallback` bean. Requires `ssrf-guard-springai` on the classpath. |
| `ssrf.guard.langchain4j.wrap-tool-executors` | `true` | Wraps every LangChain4j `ToolExecutor` bean. Requires `ssrf-guard-langchain4j` on the classpath. |

```yaml
ssrf:
  guard:
    enabled: true
    exact-hosts:
      - api.partner.com
    springai:
      wrap-tool-callbacks: true     # default
    langchain4j:
      wrap-tool-executors: true     # default
```
