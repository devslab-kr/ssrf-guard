# ssrf-guard

[English](README.md) · **한국어**

> JVM용 SSRF(Server-Side Request Forgery) 방어 — 화이트리스트 기반 outbound HTTP 가드. 사설망 차단, 리다이렉트 재검증, TOCTOU 완화, 그리고 **Spring AI 툴 URL 검증**으로 LLM 에이전트 SSRF 표면까지 차단.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/ssrf-guard.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard)
[![CI](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/ssrf-guard/branch/main/graph/badge.svg)](https://codecov.io/gh/devslab-kr/ssrf-guard)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[문서 → ssrf-guard.devslab.kr](https://ssrf-guard.devslab.kr/ko/)**

> 💬 질문, 아이디어, 사용 사례 공유는 [**devslab-examples Discussions**](https://github.com/devslab-kr/devslab-examples/discussions)에서 — 영/한 둘 다 OK, 라이브러리 만든 메인테이너가 직접 답변.

## 실행 가능한 예제

아래에 설명된 모든 모듈을 사용해보는 독립 Spring Boot 프로젝트들 — clone, `./gradlew bootRun`, curl. 복사-붙여넣기 안 해도 됨, 예제가 end-to-end로 wiring되어 있음 (스모크 테스트 포함).

| 데모 | 보여주는 것 |
| --- | --- |
| [`ssrf-guard-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-demo) | RestClient + RestTemplate + WebClient 모두 하나의 `UrlPolicy`로 wiring. 15가지 공격 매트릭스 엔드포인트, Micrometer 메트릭 |
| [`ssrf-guard-springai-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-springai-demo) | ⭐ LLM 에이전트 SSRF 방어. 가짜 LLM 드라이버 — API 키 불필요 |
| [`ssrf-guard-feign-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-feign-demo) | Spring Cloud OpenFeign `RequestInterceptor` 통합 |
| [`ssrf-guard-jdkhttp-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-jdkhttp-demo) | `java.net.http.HttpClient` 래퍼 — 라이브러리 자체엔 Spring 의존성 없음 |
| [`ssrf-guard-okhttp-demo`](https://github.com/devslab-kr/devslab-examples/tree/main/ssrf-guard-okhttp-demo) | OkHttp `Interceptor` + `Dns` 통합 — Spring 필요 없음 |

전체 인덱스: [github.com/devslab-kr/devslab-examples](https://github.com/devslab-kr/devslab-examples).

## 모듈 매트릭스

쓰는 HTTP 클라이언트에 맞는 모듈만 고르세요. `ssrf-guard-core`는 transitive로 따라옴.

| 모듈 | 용도 | Spring? |
|---|---|---|
| **`ssrf-guard`** | 메타 — RestClient + HttpClient5 (v2.0.0 호환) | ✅ |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` | ✅ |
| `ssrf-guard-resttemplate` | Spring `RestTemplate` | ✅ |
| `ssrf-guard-webclient` | Spring WebFlux `WebClient` — URL 단계 필터 + reactor-netty DNS 단계 IP 필터 (v3.1+) | ✅ |
| `ssrf-guard-feign` | Spring Cloud OpenFeign | ✅ |
| `ssrf-guard-llm` 🧩 | 프레임워크-중립 JSON 툴 입력 검증 (v3.1+) — LLM 어댑터들이 재사용 | — |
| **`ssrf-guard-springai`** ⭐ | Spring AI `ToolCallback` URL 검증 — `-llm` 위의 thin adapter | ✅ |
| **`ssrf-guard-langchain4j`** ⭐ | LangChain4j `ToolExecutor` URL 검증 — Java LLM 양대 프레임워크 다른 한쪽 (v3.1+) | ✅ |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 직접 | — |
| `ssrf-guard-jdkhttp` | `java.net.http.HttpClient` | — |
| `ssrf-guard-okhttp` | OkHttp | — |

## 무엇을 하나

서비스의 모든 outbound HTTP 호출이 소켓 열리기 전 4단계 SSRF 필터를 통과합니다:

1. **URL 단계 체크 (최전선)** — 스킴 / 호스트 / 포트 / IP 리터럴 / userinfo, 가장 저렴한 게이트에서 DNS 전에 거부. 난독화된 IP 우회 (`http://2130706433/` → `127.0.0.1`) 차단.
2. **DNS 시점 화이트리스트 재검증** — 호스트 정책을 한 번 더 적용.
3. **사설망 IP 필터** — loopback, RFC-1918, link-local (AWS 메타데이터 `169.254.169.254` 포함), CGNAT, IPv6 ULA, **IPv4-mapped IPv6 + 6to4 unmapping** (`::ffff:10.0.0.5`와 `2002:0a00::`을 사설로 정확 분류).
4. **리다이렉트 재검증** — 3xx 홉마다 동일 룰. 공격자가 `example.com` 화이트리스트 후 `169.254.169.254`로 redirect 못 시킴.

resolver가 검증한 동일 `InetAddress` 배열이 HttpClient의 `Socket.connect()`에 전달됨 — TOCTOU 윈도우 닫힘.

## Spring AI 툴 콜 — 새로운 SSRF 표면

LLM 에이전트가 URL을 툴 인자로 받으면 기본적으로 SSRF 벡터:

```java
@Tool("Fetch a URL")
String fetchUrl(String url) {
    return restClient.get().uri(url).retrieve().body(String.class);
    //          ↑ 공격자가 URL 컨트롤 — SSRF 한 줄
}
```

`ssrf-guard-springai`가 모든 `ToolCallback`을 감싸 URL 형식 인자를 정책 검증 후에만 실행되도록 막고, 거부 시 LLM이 해석/복구할 수 있는 구조화된 에러 문자열 반환.

```java
ToolCallback[] raw = ToolCallbacks.from(new MyTools());
ToolCallback[] safe = SsrfGuardedToolCallbacks.wrap(raw, urlPolicy);
```

자동 설정으로 — 모든 `@Bean ToolCallback`이 `BeanPostProcessor`로 자동 wrap.

## 설치

### Maven

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("kr.devslab:ssrf-guard:3.1.0")
```

> **v2.0.0에서 업그레이드?** 메타 `kr.devslab:ssrf-guard:3.1.0`이 v2.0.0 API 유지 — `-core`, `-httpclient5`, `-restclient`를 transitive로 끌어옴. `kr.devslab.ssrfguard.security.*`를 직접 import한 코드는 업데이트 필요 — [v3.0.0 changelog](CHANGELOG.md#300--multi-module--llm-agent-ssrf-defense) 패키지 매핑 참고.

## 설정

```yaml
ssrf:
  guard:
    enabled: true                          # 마스터 스위치
    allowed-schemes: [ "http", "https" ]
    allowed-ports:  [ -1, 80, 443 ]        # -1 = 스킴 기본 포트
    block-private-networks: true
    reject-ip-literal-hosts: true          # NEW v3.0.0 — http://127.0.0.1, http://2130706433 등 차단
    reject-user-info: true                 # NEW v3.0.0 — https://user:pass@host/... 차단
    follow-redirects: true

    # 정확 일치 화이트리스트
    exact-hosts:
      - api.partner.com
      - billing.example.org

    # 서픽스 화이트리스트 — `partner.com`은 `partner.com` 및 그 서브도메인 매치,
    # 단 `badpartner.com`은 매치 안 됨 (라벨 경계 매치).
    suffixes:
      - partner.com
      - example.org

    connect-timeout: 5s
    read-timeout: 10s
```

스타터가 classpath에 있으면 Spring Boot가 만들어주는 모든 `RestClient`가 자동으로 정책 적용 — 사용자가 별도 와이어업 안 해도 됨.

## 사용

```java
@Service
public class PartnerApi {

    private final RestClient client;

    public PartnerApi(RestClient.Builder builder) {
        this.client = builder.build();
    }

    public Customer fetch(long id) {
        // 화이트리스트 호스트 → 통과. 리스트 밖이면 연결 열리기 전에
        // SsrfGuardException 발생.
        return client.get()
                .uri("https://api.partner.com/customers/{id}", id)
                .retrieve()
                .body(Customer.class);
    }
}
```

화이트리스트 아닐 때:

```text
kr.devslab.ssrfguard.core.SsrfGuardException: Host not allowed: evil.com
    (reason=blocked_host, scheme=https, host=evil.com)
    at kr.devslab.ssrfguard.core.UrlPolicy.reject(...)
```

`SsrfGuardException extends SecurityException` — v2.0.0의 `catch (SecurityException e)` 코드는 그대로 동작. 새 타입으로 catch하면 `e.reason()` (`BlockReason` enum) 접근 가능.

## 관찰성 (Micrometer 자동)

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

차단마다 구조화된 WARN 로그:

```
WARN k.d.s.core.UrlPolicy : ssrf-guard: Host not allowed: evil.com (reason=blocked_host, scheme=https, host=evil.com)
```

태그가 bounded — Prometheus / Datadog / CloudWatch 문제 없음.

## 성능

인터셉터의 allowed 경로 비용은 **요청당 ~5 μs** (JMH, JDK 21) — 100 ms 외부 API 호출 대비 **0.005% 오버헤드**, 실질적으로 invisible.

| Hot path | 평균 비용 | 비고 |
|---|---:|---|
| `UrlPolicy.validate` allowed | ~5 μs | 프로덕션 트래픽의 99%+ |
| `UrlPolicy.validate` blocked | 5-12 μs | early-exit (IP 리터럴)이 late-exit (whitelist)보다 저렴 |
| `JsonToolInputGuard` 작은 JSON | ~6 μs | URL 하나짜리 LLM 툴 입력 |
| `JsonToolInputGuard` ~2 KB JSON | ~24 μs | URL 3개 있는 RAG-augmented 툴 입력 |

전체 방법론, stdev 포함 케이스별 숫자, 해석 가이드는 [`BENCHMARKS.md`](./BENCHMARKS.md). 직접 재현: `./gradlew :ssrf-guard-benchmarks:jmh`.

## 자동 구성이 등록하는 빈 (RestClient 모듈)

`ssrf.guard.enabled=true`(기본값)이면 RestClient 자동설정이 활성화되어 다음을 등록:

- `SafeDnsResolver` — 화이트리스트 + 사설 IP 필터. Apache HttpClient 5 connection manager에 연결
- `CloseableHttpClient` — resolver와 (리다이렉트 활성 시) `SafeRedirectStrategy` 와이어업
- `HttpComponentsClientHttpRequestFactory` — connect/read timeout 적용
- `UrlPolicy` — 최전선 URL 단계 게이트 (스킴, 호스트, 포트, IP 리터럴, userinfo)
- `SsrfGuardClientHttpRequestInterceptor` — Spring `ClientHttpRequestInterceptor`, 정책에 위임
- `SsrfGuardMetrics` — `MeterRegistry` 있으면 Micrometer 기반, 없으면 no-op
- `RestClientCustomizer` (`ssrfRestClientCustomizer`) — factory + interceptor를 Spring Boot 자동 `RestClient.Builder`에 핀

모듈마다 자기 자동설정 보유 — `SsrfGuardRestTemplateAutoConfiguration`, `SsrfGuardWebClientAutoConfiguration`, `SsrfGuardFeignAutoConfiguration`, `SsrfGuardSpringAiAutoConfiguration`. 동일 `UrlPolicy`와 `SsrfGuardMetrics` 빈 공유. 모든 빈이 `@ConditionalOnMissingBean` — 일부만 교체 가능.

## 요구사항

- Java 21+
- Spring Boot 3.5+ (Spring 기반 모듈)
- Spring AI 1.0+ (`springai` 모듈)
- Spring Cloud 2024.0+ (`feign` 모듈)
- Apache HttpClient 5 (`-httpclient5`, `-restclient`, `-resttemplate`이 transitive로 끌어옴)

## 라이선스

Apache License 2.0 — [LICENSE](LICENSE), [NOTICE](NOTICE) 참고.

---

[Devslab](https://devslab.kr) 제작 · [DevsLab 오픈소스 툴킷](https://github.com/devslab-kr) 일부.
