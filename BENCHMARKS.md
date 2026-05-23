# Benchmarks

JMH micro-benchmarks measuring the hot paths consumers pay for when they add ssrf-guard to a project.

The numbers are **per-call** (nanoseconds), since the practical question is "how much do I add to my request latency". A single HTTP request typically takes hundreds of microseconds to hundreds of milliseconds end-to-end; the policy check on the realistic allowed path measures **~5 μs**, which is **~0.005% overhead** on a 100 ms remote API call and **~0.5%** on a 1 ms in-cluster call — both well inside the noise floor of network jitter.

## How to run

```bash
./gradlew :ssrf-guard-benchmarks:jmh
```

Results land at `ssrf-guard-benchmarks/build/results/jmh/results.txt` (and `.csv` / `.json` if you tweak the [`jmh { ... }`](./ssrf-guard-benchmarks/build.gradle.kts) block in the build script).

## Methodology

- **JVM**: Java 21 (Temurin), default JIT (HotSpot C2)
- **JMH**: 1.37 via [`me.champeau.jmh` 0.7.2](https://github.com/melix/jmh-gradle-plugin)
- **Mode**: `AverageTime`, `ns/op`
- **Forks**: 1
- **Warmup**: 5 iterations × 1 second each
- **Measurement**: 5 iterations × 1 second each
- **State scope**: `Benchmark` (one shared instance across threads; we don't measure contention here)

Single-fork is intentionally light for fast local iteration. For canonical numbers (e.g. for a blog post or commit message) bump `fork = 3` in [`build.gradle.kts`](./ssrf-guard-benchmarks/build.gradle.kts) to get JIT variance information.

## Benchmarks

### `UrlPolicyBenchmark` — the per-request hot path

Every ssrf-guard HTTP-client interceptor (RestClient / RestTemplate / WebClient / Feign / OkHttp) calls `UrlPolicy.validate(URI)` once per request. The benchmark measures three URL classes:

| Benchmark | URL | What it exercises |
| --- | --- | --- |
| `allowed` | `https://api.partner.com/v1/users/42` | Full happy path — scheme + port + IP-literal + userinfo + whitelist all pass |
| `blockedIpLiteral` | `http://169.254.169.254/...` (AWS metadata) | Fails at the IP-literal-host check after URL parse |
| `blockedHost` | `https://evil.com/exfiltrate` | Fails at the whitelist lookup — the most expensive blocked path |

The blocked cases include `SsrfGuardException` construction in the measurement, because that's what interceptors really pay on rejection.

URI parsing is done at `@Setup` (not in the measurement window) — real interceptors get a pre-parsed `URI` from the HTTP client, so including parse cost would be misleading.

### `JsonToolInputGuardBenchmark` — the per-LLM-tool-call hot path

`ssrf-guard-springai` and `ssrf-guard-langchain4j` both call `JsonToolInputGuard.checkOrFormatError(input)` once per LLM tool invocation. The guard parses the JSON, walks the tree, runs `UrlPolicy.validate` for each URL-shaped value, and on rejection returns a `SsrfBlockPayload` JSON string the LLM can interpret.

| Benchmark | Input | Size |
| --- | --- | --- |
| `small_allowed` | `{"url":"https://api.partner.com/..."}` | ~50 bytes |
| `medium_blocked` | Nested object with a blocked URL two levels deep | ~150 bytes |
| `large_allowed` | 3 URLs + 40 non-URL fields | ~2 KB |

`small_allowed` is the floor cost — what a one-URL `fetch_url` tool pays. `large_allowed` is closer to what a RAG-augmented tool with structured input looks like.

## Results

> **Note**: numbers below are from a single run on the maintainer's machine and are intended for **relative comparison only** (allowed vs blocked, small vs large). Absolute numbers will vary 30-50% across machines, JDK builds, and CPU thermal states. Run locally for your own hardware before quoting.

### `UrlPolicyBenchmark`

```
Benchmark                            Mode  Cnt      Score      Error  Units
UrlPolicyBenchmark.allowed           avgt    5   5285.614 ±  923.936  ns/op
UrlPolicyBenchmark.blockedIpLiteral  avgt    5   4888.770 ±  749.687  ns/op
UrlPolicyBenchmark.blockedHost       avgt    5  11822.612 ± 2034.582  ns/op
```

| Benchmark | Score | Error | μs | Notes |
| --- | ---: | ---: | ---: | --- |
| `allowed` | 5,285 | ± 924 | ~5 μs | The happy path — what 99%+ of production traffic costs |
| `blockedIpLiteral` | 4,888 | ± 750 | ~5 μs | Slightly **faster** than allowed because the IP-literal check fires before the whitelist lookup |
| `blockedHost` | 11,822 | ± 2,035 | ~12 μs | The most expensive blocked path — passes scheme/port/IP-literal/userinfo, then fails whitelist; includes `SsrfGuardException` construction |

### `JsonToolInputGuardBenchmark`

```
Benchmark                                   Mode  Cnt      Score      Error  Units
JsonToolInputGuardBenchmark.small_allowed   avgt    5   5629.195 ±  485.552  ns/op
JsonToolInputGuardBenchmark.medium_blocked  avgt    5   6721.888 ±  289.901  ns/op
JsonToolInputGuardBenchmark.large_allowed   avgt    5  24228.533 ± 4958.066  ns/op
```

| Benchmark | Score | Error | μs | Notes |
| --- | ---: | ---: | ---: | --- |
| `small_allowed` | 5,629 | ± 486 | ~6 μs | One URL, top-level — the floor cost for a `fetch_url`-style tool |
| `medium_blocked` | 6,722 | ± 290 | ~7 μs | Nested URL two levels deep, blocked at IP-literal check; includes `SsrfBlockPayload` JSON serialization |
| `large_allowed` | 24,228 | ± 4,958 | ~24 μs | 3 URLs + 40 non-URL fields (~2 KB JSON) — closer to a RAG-augmented tool input |

The JSON guard is roughly **Jackson parse cost + N × UrlPolicy.validate**. On the `large_allowed` (3 URLs) input, the guard is ~24 μs ≈ (Jackson parse for 2 KB) + (3 × ~5 μs URL checks) + (tree walk overhead).

### Practical takeaway

| Your request latency | ssrf-guard `allowed` cost | Overhead |
| --- | --- | --- |
| 1 ms (in-cluster call) | ~5 μs | ~0.5% |
| 10 ms (in-region call) | ~5 μs | ~0.05% |
| 100 ms (remote API) | ~5 μs | ~0.005% |
| 500 ms (slow external API / LLM tool call) | ~5 μs | ~0.001% |

For an LLM-driven agent making a tool call (typically 100 ms - 5 s end-to-end including the LLM round-trip + the actual fetch), `JsonToolInputGuard` adds **~6-24 μs**, which is **invisible**.

### Test environment

| | |
| --- | --- |
| JDK | Temurin 21 |
| OS | Windows 11 (development machine) |
| CPU | Recent x86_64 desktop |
| Forks | 1 (development setup — bump to 3 for canonical numbers) |

## How to read these numbers

- **`allowed` is what 99%+ of your traffic costs.** A blocked request is by definition rare in production (you'd fix the policy or the bug). Quote `allowed` when answering "what's the overhead in steady state?".
- **Compare to your real request latency.** Even at 5 μs per check, that's ~0.5% overhead on a 1ms in-cluster call and basically invisible on a 100ms remote API call. If you're worried about it, you have a hotter path than ssrf-guard.
- **Don't compare `JsonToolInputGuard` to `UrlPolicy` directly.** The JSON guard does a full Jackson parse + tree walk + `SsrfBlockPayload` serialization on rejection. Apples-to-oranges with the URL-time interceptor.
- **The block path is more expensive than the allow path** for both benchmarks, because exception construction is in the measurement. This is realistic — your code paying for a block is the price of stopping an attack.

## What this doesn't measure

Deliberately out of scope (and what would be wrong to extrapolate from these numbers):

- **DNS-time gates** (`ssrf-guard-httpclient5`'s `SafeDnsResolver`, `ssrf-guard-okhttp`'s `Dns` SPI, `ssrf-guard-webclient`'s reactor-netty `AddressResolverGroup`). DNS resolution is dominated by I/O and depends on your resolver's RTT — meaningless to micro-benchmark without mocking the network, and you'd be measuring the mock rather than the gate.
- **Spring auto-configuration startup cost.** A one-time tax at app boot, not a per-request concern.
- **Micrometer metrics overhead.** Benchmarks use `NoOpSsrfGuardMetrics` to isolate the guard's own cost. Add Micrometer to a real app and you pay whatever Micrometer's `Counter.increment()` costs (typically ~50 ns).
- **Native-image (GraalVM) performance.** The JIT-optimized HotSpot numbers above are an upper bound; AOT-compiled native binaries skip the JIT but have their own performance shape. Run the same JMH harness under `nativeRun` to compare for your deployment target.

## Adding a new benchmark

1. Drop a class under `ssrf-guard-benchmarks/src/jmh/java/kr/devslab/ssrfguard/bench/`
2. Use the JMH annotations matching the existing benchmarks (same fork / warmup / measurement / mode for comparability)
3. Run `./gradlew :ssrf-guard-benchmarks:jmh` — JMH auto-discovers it via the `includes` regex in `build.gradle.kts`
4. Paste the score row into the appropriate table above
