# Roadmap

[한국어](roadmap.ko.md)

What is done, what is queued, and what is deliberately not planned.
Started 2026-08-09.

Sibling: [`@devslab/ssrf-guard-js`](https://github.com/devslab-kr/ssrf-guard-js)
ships the same security model in JS/TS on its own version line — same
model, not the same release train.

## Current state

- **Published:** `kr.devslab:ssrf-guard` **3.3.0** (Maven Central, 2026-08-09)
- **Line:** single Spring Boot 3 line (`3.x.y`); the library major tracks
  the Spring Boot major, per the org's
  [versioning policy](https://github.com/devslab-kr/.github/blob/main/.github/VERSIONING.md)
- **Modules:** core, llm, six HTTP-client adapters (RestTemplate,
  RestClient, WebClient, Feign, OkHttp, JdkHttp, HttpClient5), two LLM
  adapters (Spring AI, LangChain4j), plus benchmarks
- **Suite:** 239 tests, green as of 2026-08-09
- **Demos:** eight in [devslab-examples](https://github.com/devslab-kr/devslab-examples)

## Next

**One named piece of work: OkHttp redirect hops.**

3.3.0 gave every hop the same checks as the first request, through a
single core definition ([`RedirectGuard`](https://github.com/devslab-kr/ssrf-guard/blob/main/ssrf-guard-core/src/main/java/kr/devslab/ssrfguard/core/RedirectGuard.java)),
for `jdkhttp` and `httpclient5`. **OkHttp was left out**, and the reason is
worth reading before anyone tries again:

- Its `Dns` layer still re-checks the host allowlist and private IPs on
  every hop, so the narrow residual risk is the **same allowlisted host**
  on another port or scheme, or with userinfo — not a route to an internal
  address.
- **A network interceptor is not the seam.** OkHttp invokes those *after*
  the connection is established. An attempt failed with
  `SocketException: Network is unreachable` against a metadata address —
  the socket had already opened. For SSRF the connection *is* the attack,
  so that fix would have looked right in review and been wrong.
- Closing it properly needs the treatment `jdkhttp` got: disable OkHttp's
  own redirect following and drive the loop, re-validating each hop
  through `RedirectGuard`. That changes the adapter's contract, so it
  belongs in its own release rather than stacked on one that already
  carries a breaking change.

Tracked in the sibling's
[parity audit](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.md),
which is where cross-library findings live.

## Shipped

| Version | What |
| --- | --- |
| 3.3.0 | Redirect-hop parity via core `RedirectGuard`; the tool-input scanner stopped dropping non-`http(s)` schemes and protocol-relative URLs before the policy saw them. One breaking change — `SsrfGuardedHttpClient` requires a `Redirect.NEVER` delegate |
| 3.2.0 | `scanEmbedded` for the LLM tool-input guard; tool-input URLs `java.net.URI` cannot parse no longer skip validation |
| 3.1.1 | **Security:** uppercase-scheme bypass in the LLM tool-input guard |
| 3.1.0 | LLM core extraction, LangChain4j adapter, WebClient DNS gap, GraalVM hints |

Full notes in the [changelog](changelog.md).

## Standing practice

Not features, but things that should keep happening:

- **Walk the [parity checklist](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.md)
  whenever core logic changes here** — scanner collection, IP
  classification, redirect semantics, block reasons. Round 1 found
  divergences in both libraries, including two on this side that had let
  URLs skip validation entirely. Compare *behaviour*, not source: reading
  the two implementations side by side is how the 3.1.1 bypass survived
  review in both at once.
- **Follow [the release runbook](releasing.md)** — in particular, check
  the tag and Maven Central before assuming a version is unreleased.

## Not planned

- **Matching the JS sibling's feature set.** `maxBytes`, `checkUrl` and
  the policy helpers are JS-only so far. A JS-only feature is not a parity
  failure; a feature both sides have, implemented differently, is.
- **A Spring Boot 4 line**, until there is demand. The org's versioning
  policy makes that a `4.x.y` line, not a rename.
