# Security model

ssrf-guard is four independent gates, each closing a different SSRF bypass. Read this to know what each gate guarantees, where it doesn't, and what you still need to think about.

## The four gates

| Gate | When | What it checks | Failure mode |
|---|---|---|---|
| **`SsrfGuardInterceptor`** | Before DNS | URL scheme, host (whitelist match), port | `SecurityException` |
| **`SafeDnsResolver`** (whitelist) | At DNS resolution | Host (whitelist match again) | `UnknownHostException` |
| **`SafeDnsResolver`** (IP filter) | At DNS resolution | Each resolved IP is not in private/loopback/link-local/multicast/CGNAT/benchmark/IPv6-ULA | `UnknownHostException` if nothing left after filter |
| **`SafeRedirectStrategy`** | On every 3xx | Re-runs scheme + DNS-resolver against the redirect target | `RedirectException` |

Each gate runs even if the others have already passed — defense in depth. An attacker has to bypass every layer to land an outbound call.

## Why the whitelist is checked twice

The naïve "check the URL once, then make the request" pattern has a race condition:

1. App parses URL, extracts host
2. App checks whitelist on the host string
3. App passes URL to HTTP client
4. HTTP client resolves DNS — gets back a different IP than the host implies
5. Connection happens to that different IP

Between (2) and (4) the URL string changes meaning. ssrf-guard solves this two ways:

- **The whitelist check runs again at DNS time** (`SafeDnsResolver.resolve()`), so the host the resolver sees has to be the same one the interceptor accepted.
- **The returned `InetAddress[]` is what HttpClient passes to `Socket.connect()` directly** — there is no second DNS lookup between resolve and connect. The IP the resolver validated is the IP the socket opens to.

That's the "TOCTOU mitigation" line in the project description.

## What `block-private-networks` blocks

Set to `true` by default. Resolves matching any of:

- **Loopback**: `127.0.0.0/8`, `::1`
- **RFC 1918**: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
- **Link-local**: `169.254.0.0/16` (includes AWS metadata at `169.254.169.254`), `fe80::/10`
- **CGNAT**: `100.64.0.0/10`
- **Benchmark**: `198.18.0.0/15`
- **Multicast**: `224.0.0.0/4`, `ff00::/8`
- **IPv6 ULA**: `fc00::/7`
- **Any-local**: `0.0.0.0`, `::`
- **Broadcast**: `255.255.255.255`

Java's built-in `InetAddress.isSiteLocalAddress()` misses CGNAT, the benchmark range, and most of the IPv6 categories — `NetUtil.isPrivateOrLocal()` is hand-rolled to cover all of them.

## What ssrf-guard does NOT do

Honest list. Knowing the boundary is part of the threat model.

- **It does not validate URL parsing the way every HTTP client interprets it.** The JDK `URI` constructor, Spring's `UriComponentsBuilder`, Apache HttpClient's request line — they don't always agree on what host a `://user:pass@a.com\@b.com/` string represents. If you accept URLs from untrusted input, normalize them before handing them to `RestClient`. The OWASP cheat sheet has a [URL parser confusion section](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html#network-layer) worth reading.
- **It does not protect non-wrapped HTTP clients.** Code using raw `HttpURLConnection`, or any client built outside the auto-configured `RestClient.Builder` / `RestTemplateBuilder` / `WebClient.Builder` / `OkHttpClient.Builder` etc. — that custom code bypasses the SSRF policy. v3.1+ covers every major Java HTTP stack (RestClient · RestTemplate · WebClient · Feign · OkHttp · JDK `HttpClient` · Apache HttpClient 5) plus LLM tool dispatch (Spring AI · LangChain4j) — pick the matching module for each one you use.
- **WebClient gets URL-time *and* DNS-time defense from v3.1.** v3.0.x's WebClient module only ran the URL-time filter — a host that passed the whitelist could still resolve to a private IP, and reactor-netty would connect to it. v3.1's `SsrfGuardReactorAddressResolverGroup` hooks into reactor-netty's address resolver and filters resolved IPs against the same private/loopback ranges. Non-Netty WebFlux backends (Jetty Reactive, Helidon) still get URL-time defense; the connector swap is gated on reactor-netty being on the classpath.
- **It does not protect against DNS rebinding when the JVM cache is in play.** Java caches DNS resolutions; if your JVM caches forever (the default for security policies that pre-date Java 8u192) and a hostname's records change after the cache was populated, the cached IP is what you keep hitting. Set `networkaddress.cache.ttl` to something sane (default in modern JVMs is already 30 seconds).
- **It does not stop you from putting a private-IP literal directly in `exact-hosts`.** If you whitelist `10.0.0.5`, the interceptor accepts that host, and the DNS resolver short-circuits to that IP. The private-IP filter still applies (unless you set `block-private-networks=false`), so the request is rejected — but the layering is "interceptor accepts, resolver rejects," not "interceptor rejects up front."
- **It does not validate response bodies.** A whitelisted host can still return content that triggers downstream issues. SSRF defense ends when the socket connects to a host you trust; what that host returns is application logic.

## Threat-model checklist

If you're using ssrf-guard, you should also:

1. **Run with `block-private-networks=true`** unless you have a specific reason to allow internal calls. The default is `true` precisely because turning it off is the most common way to accidentally re-enable SSRF.
2. **Keep `follow-redirects=true`** unless you have a specific reason to forbid redirects. Disabling redirects is sometimes a defense in depth move but it tends to break legitimate API integrations.
3. **Treat the whitelist as security-critical config.** Anyone who can write to it can effectively bypass ssrf-guard. Run config diffs through code review.
4. **Don't let your URL come from a string concat with user input.** Even with ssrf-guard active, `https://api.partner.com/proxy?target=` + user-supplied URL is its own SSRF (you become the attacker's proxy). Validate the user-supplied URL before composing.
5. **Monitor for `SecurityException: Host not allowed`** in logs. Either it's an attacker probing, or it's a legitimate integration that needs a whitelist update.

The OWASP [SSRF prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) is worth re-reading every six months.
