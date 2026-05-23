# Performance

The allowed-path cost of the interceptor is **~5 μs per request** on JDK 21 — 0.005% overhead on a 100 ms remote API call, invisible in practice. The numbers below come from JMH micro-benchmarks against the actual hot paths consumers pay for. Reproduce locally with `./gradlew :ssrf-guard-benchmarks:jmh`.

## What's measured

| Module | Method | Why it matters |
|---|---|---|
| `ssrf-guard-core` | `UrlPolicy.validate(URI)` | What every HTTP-client interceptor (RestClient / RestTemplate / WebClient / Feign / OkHttp) invokes once per request |
| `ssrf-guard-llm` | `JsonToolInputGuard.checkOrFormatError(json)` | What `-springai` and `-langchain4j` invoke once per LLM tool call |

The DNS-time gates (`SafeDnsResolver` in `-httpclient5`, OkHttp `Dns`, reactor-netty `AddressResolverGroup` in `-webclient`) aren't micro-benchmarked here — DNS resolution is dominated by network I/O, not by the policy code that runs around it. Measuring with a mock resolver would just measure the mock.

## Results

JMH 1.37, JDK 21, single-fork, 5×1s warmup + 5×1s measurement.

### `UrlPolicy.validate(URI)` — per-request

| Benchmark | Score | Error | Notes |
|---|---:|---:|---|
| `allowed` | 5,285 ns | ± 924 ns | The happy path — what 99%+ of production traffic costs |
| `blockedIpLiteral` | 4,888 ns | ± 750 ns | **Faster** than allowed — IP-literal check fires before whitelist lookup |
| `blockedHost` | 11,822 ns | ± 2,035 ns | The most expensive blocked path — full chain + `SsrfGuardException` |

### `JsonToolInputGuard` — per-LLM-tool-call

| Benchmark | Score | Error | Notes |
|---|---:|---:|---|
| `small_allowed` | 5,629 ns | ± 486 ns | `{"url": "..."}` — floor cost for a `fetch_url`-style tool |
| `medium_blocked` | 6,722 ns | ± 290 ns | Nested URL two levels deep, blocked at IP-literal check |
| `large_allowed` | 24,228 ns | ± 4,958 ns | ~2 KB JSON, 3 URLs + 40 non-URL fields (RAG-shaped) |

The JSON guard scales roughly as `Jackson parse + N × UrlPolicy.validate`.

## Practical takeaway

| Your request latency | ssrf-guard `allowed` cost | Overhead |
|---|---|---|
| 1 ms (in-cluster call) | ~5 μs | ~0.5% |
| 10 ms (in-region call) | ~5 μs | ~0.05% |
| 100 ms (remote API) | ~5 μs | ~0.005% |
| 500 ms (LLM tool call) | ~5 μs | ~0.001% |

For an LLM-driven agent making a tool call (100 ms - 5 s end-to-end including the LLM round-trip), `JsonToolInputGuard` adds **~6-24 μs**, which is invisible against the LLM cost.

## How to read these numbers

- **Quote `allowed` when answering "what's the steady-state overhead?"**. The blocked-path costs are realistic on rejection but rare — a blocked request in production is a misconfiguration or an actual attack, not the common case.
- **Compare against your real request latency**. Even at 5 μs per check, that's well inside the noise floor of network jitter on any out-of-process call.
- **Don't compare `JsonToolInputGuard` to `UrlPolicy` directly** — the JSON guard parses Jackson + walks the tree + serialises a block payload on rejection. Apples-to-oranges with the URL-time interceptor.
- **The block path is more expensive than the allow path** for both benchmarks, because exception construction is in the measurement. This is realistic — paying for a block is the price of stopping an attack.

## What this doesn't measure

Deliberately out of scope (and what would be wrong to extrapolate from these numbers):

- **DNS-time gates** — dominated by I/O; meaningless to micro-benchmark without mocking
- **Spring auto-configuration startup cost** — one-time tax at app boot, not per-request
- **Micrometer metrics overhead** — benchmarks use `NoOpSsrfGuardMetrics` to isolate the guard's own cost. Real Micrometer adds ~50 ns per `Counter.increment()` on top
- **Native-image (GraalVM) performance** — different shape; JIT numbers above are an upper bound. Run the same JMH harness under `nativeRun` for AOT.

## Reproducing

```bash
./gradlew :ssrf-guard-benchmarks:jmh
```

Output lands at `ssrf-guard-benchmarks/build/results/jmh/results.txt`.

For canonical numbers (e.g. a blog post or a commit message), bump `fork = 3` in [`ssrf-guard-benchmarks/build.gradle.kts`](https://github.com/devslab-kr/ssrf-guard/blob/main/ssrf-guard-benchmarks/build.gradle.kts) to capture JIT variance.

Full canonical source — methodology in narrative form, contribution guide for new benchmarks — at [`BENCHMARKS.md`](https://github.com/devslab-kr/ssrf-guard/blob/main/BENCHMARKS.md).
