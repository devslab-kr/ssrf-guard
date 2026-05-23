# 성능

인터셉터의 allowed 경로 비용은 JDK 21 기준 **요청당 ~5 μs** — 100 ms 외부 API 호출 대비 0.005% 오버헤드, 실질적으로 invisible. 아래 숫자들은 실제 사용자가 비용을 지불하는 hot path에 대한 JMH 마이크로벤치마크. 로컬 재현: `./gradlew :ssrf-guard-benchmarks:jmh`.

## 무엇을 측정하나

| 모듈 | 메서드 | 왜 중요한가 |
|---|---|---|
| `ssrf-guard-core` | `UrlPolicy.validate(URI)` | 모든 HTTP 클라이언트 인터셉터 (RestClient / RestTemplate / WebClient / Feign / OkHttp)가 요청마다 한 번 호출 |
| `ssrf-guard-llm` | `JsonToolInputGuard.checkOrFormatError(json)` | `-springai`와 `-langchain4j`가 LLM 툴 호출마다 한 번 호출 |

DNS 시점 게이트 (`-httpclient5`의 `SafeDnsResolver`, OkHttp `Dns`, `-webclient`의 reactor-netty `AddressResolverGroup`)는 마이크로벤치마크 대상 아님 — DNS 해석은 네트워크 I/O가 지배. 모킹된 resolver로 측정해봐야 모킹을 측정하는 셈.

## 결과

JMH 1.37, JDK 21, single-fork, 5×1s warmup + 5×1s measurement.

### `UrlPolicy.validate(URI)` — 요청당

| 벤치마크 | Score | Error | 비고 |
|---|---:|---:|---|
| `allowed` | 5,285 ns | ± 924 ns | 해피 패스 — 프로덕션 트래픽의 99%+ |
| `blockedIpLiteral` | 4,888 ns | ± 750 ns | allowed보다 **빠름** — IP 리터럴 검사가 화이트리스트 lookup보다 먼저 |
| `blockedHost` | 11,822 ns | ± 2,035 ns | 가장 비싼 차단 경로 — 전체 체인 + `SsrfGuardException` |

### `JsonToolInputGuard` — LLM 툴 호출당

| 벤치마크 | Score | Error | 비고 |
|---|---:|---:|---|
| `small_allowed` | 5,629 ns | ± 486 ns | `{"url": "..."}` — `fetch_url` 류 툴의 floor 비용 |
| `medium_blocked` | 6,722 ns | ± 290 ns | URL이 2단계 깊이로 중첩, IP 리터럴 검사에서 차단 |
| `large_allowed` | 24,228 ns | ± 4,958 ns | ~2 KB JSON, URL 3개 + 비 URL 필드 40개 (RAG 형태) |

JSON 가드는 대략 `Jackson 파싱 + N × UrlPolicy.validate` 스케일.

## 실용적 해석

| 본인 요청 레이턴시 | ssrf-guard `allowed` 비용 | 오버헤드 |
|---|---|---|
| 1 ms (클러스터 내부 호출) | ~5 μs | ~0.5% |
| 10 ms (리전 내부 호출) | ~5 μs | ~0.05% |
| 100 ms (외부 API) | ~5 μs | ~0.005% |
| 500 ms (LLM 툴 호출) | ~5 μs | ~0.001% |

LLM 에이전트가 툴 호출 (LLM 왕복 + 실제 fetch 포함 100 ms - 5 s end-to-end) 하나 할 때, `JsonToolInputGuard`는 **~6-24 μs** 추가 — LLM 비용 대비 invisible.

## 숫자 해석 가이드

- **"steady-state 오버헤드가 얼마?" 라는 질문엔 `allowed` 인용**. 차단 경로 비용은 거부 시점에는 실재하지만 production에서는 드묾 — 차단된 요청은 설정 미스 또는 실제 공격, 일반적이지 않음.
- **실제 요청 레이턴시와 비교**. 5 μs라도 어떤 out-of-process 호출의 network jitter noise floor 안에 들어감.
- **`JsonToolInputGuard`와 `UrlPolicy`를 직접 비교 금지** — JSON 가드는 Jackson 파싱 + 트리 walk + 차단 페이로드 직렬화 (거부 시). URL-time 인터셉터와는 apples-to-oranges.
- **차단 경로가 허용 경로보다 비쌈** — 예외 생성이 측정에 포함됨. 현실적임 — 차단 비용은 공격을 막는 대가.

## 측정 안 한 것

의도적으로 범위 밖 (그리고 이 숫자에서 추론하면 안 되는 것):

- **DNS 시점 게이트** — I/O 지배; 모킹 없이 마이크로벤치마크 의미 없음
- **Spring 자동 설정 시작 비용** — 앱 부트 시 1회 비용, 요청당 비용 아님
- **Micrometer 메트릭 오버헤드** — 벤치마크는 `NoOpSsrfGuardMetrics` 사용해서 가드 자체 비용 격리. 실제 Micrometer는 `Counter.increment()`당 ~50 ns 추가
- **네이티브 이미지 (GraalVM) 성능** — 다른 형태; 위의 JIT 숫자가 상한. AOT 비교는 `nativeRun` 아래에서 같은 JMH harness 실행.

## 재현

```bash
./gradlew :ssrf-guard-benchmarks:jmh
```

출력: `ssrf-guard-benchmarks/build/results/jmh/results.txt`.

정식 숫자 (블로그 글이나 커밋 메시지용)는 [`ssrf-guard-benchmarks/build.gradle.kts`](https://github.com/devslab-kr/ssrf-guard/blob/main/ssrf-guard-benchmarks/build.gradle.kts)에서 `fork = 3`으로 올려서 JIT 분산 캡처.

정식 원본 — 방법론, 새 벤치마크 기여 가이드 — [`BENCHMARKS.md`](https://github.com/devslab-kr/ssrf-guard/blob/main/BENCHMARKS.md).
