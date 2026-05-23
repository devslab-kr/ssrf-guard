# 변경 이력

ssrf-guard의 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 본 프로젝트는 [Semantic Versioning](https://semver.org/lang/ko/)을 준수합니다.

## [Unreleased]

## [3.0.1] — 메트릭 빈 classpath 게이트 수정

### Fixed

- **`ClassNotFoundException: io.micrometer.core.instrument.MeterRegistry`** — `micrometer-core`가 classpath에 없는 소비자가 부팅 실패. `-restclient`, `-resttemplate` (via `-restclient`), `-webclient`, `-feign`, `-springai` 전부 영향. 메트릭 빈 팩토리 메서드가 `ObjectProvider<MeterRegistry>`를 파라미터로 선언했고, `ObjectProvider`는 빈이 없을 때 우아하게 처리하지만 파라미터 타입 자체는 클래스 로드 시점에 JVM이 resolve하기 때문.
- Micrometer 기반 메트릭 빈을 static inner `@Configuration`으로 격리하고 `@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")` (string form — Spring ASM 조건 평가기가 JVM 로드 없이 어노테이션 검사)으로 게이트. 외부 자동설정은 `@ConditionalOnMissingBean(SsrfGuardMetrics.class)` 조건으로 `NoOpSsrfGuardMetrics` fallback `@Bean` 등록.

### Migration

v3.0.1로 그냥 올리기 — 소비자 코드 변경 없음. v3.0.0의 이 버그를 우회하려고 `io.micrometer:micrometer-core`를 빌드에 추가했었다면(메트릭 실제로 안 쓰면서) 이제 제거 가능.

## [3.0.0] — 멀티모듈 + LLM 에이전트 SSRF 방어

v2.0.0 스타터는 단일 jar로 Spring `RestClient`만 지원했지만, v3.0.0은 HTTP 클라이언트 경계로 코드를 분할하고 모든 JVM HTTP 스택에 대한 모듈을 추가했으며, 지난 2년간 LLM 에이전트가 만들어온 SSRF 표면을 막는 **Spring AI Tool 래퍼**를 출시합니다.

### Added — 새 모듈 (opt-in)

| 모듈 | 용도 |
|---|---|
| `ssrf-guard-core` | 정책 / NetUtil / Micrometer 메트릭 인터페이스 — Spring 의존성 없음 |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 `DnsResolver` + `RedirectStrategy` (TOCTOU 차단) |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` 자동설정 (v2.0.0의 surface가 별도 모듈로) |
| `ssrf-guard-resttemplate` | **NEW** — 엔터프라이즈/레거시용 Spring `RestTemplate` 자동설정 |
| `ssrf-guard-webclient` | **NEW** — Spring WebFlux `WebClient` `ExchangeFilterFunction` + 자동설정 |
| `ssrf-guard-feign` | **NEW** — Spring Cloud OpenFeign `RequestInterceptor` + 자동설정 |
| `ssrf-guard-springai` | **NEW** — Spring AI `ToolCallback` 래퍼. LLM이 실행하기 전 URL 형식 인자 검증. 2025+ 가장 핫한 SSRF 표면 |
| `ssrf-guard-jdkhttp` | **NEW** — `java.net.http.HttpClient` 래퍼 (Spring 없음, JDK 11+) |
| `ssrf-guard-okhttp` | **NEW** — OkHttp `Interceptor` + `Dns` (Spring 없음) |
| `ssrf-guard` | 메타 아티팩트 — `-core` + `-httpclient5` + `-restclient` 묶음으로 v2.0.0 호환 |

### Added — 방어 강화

- **IP 리터럴 호스트 거부** (`ssrf.guard.reject-ip-literal-hosts=true` 기본). 호스트가 어떤 형식의 IP 리터럴이든 — dotted decimal (`127.0.0.1`), bare decimal (`2130706433`), hex (`0x7f000001`), octal (`0177.0.0.1`), 부분 (`127.1`), IPv6 (`[::1]`) — URL 단계에서 DNS 전에 거부. 난독화된 IP 우회 클래스를 통째로 차단.
- **Userinfo 거부** (`ssrf.guard.reject-user-info=true` 기본). `https://user:pass@host/...` 형식 거부 — 알려진 SSRF 우회 벡터이자 credential 누출 리스크.
- **IPv4-mapped IPv6 + 6to4 unmapping**. `::ffff:10.0.0.5`와 `2002:0a00::` (10.0.0.0/8을 wrap한 6to4)이 이제 사설로 정확히 분류됨 — Java의 `isLoopbackAddress()`가 놓치던 우회.

### Added — 관찰성

- **Micrometer 메트릭**. 클래스패스에 `MeterRegistry` 빈이 있으면 자동:
  ```
  ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
  ssrf_guard_allowed_total{scheme="https"} 13042
  ```
- **구조화된 WARN 로그.** 차단마다: `ssrf-guard: <message> (reason=blocked_private_ip, scheme=http, host=169.254.169.254)`.

### Changed — BREAKING

- **패키지 변경.** catch-all `kr.devslab.ssrfguard.security` 패키지의 타입들이 각 모듈의 패키지로 이동:

  | v2.0.0 | v3.0.0 |
  | --- | --- |
  | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardAutoConfiguration` | `kr.devslab.ssrfguard.restclient.SsrfGuardRestClientAutoConfiguration` |
  | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardProperties` | `kr.devslab.ssrfguard.core.SsrfGuardProperties` |
  | `kr.devslab.ssrfguard.security.SsrfGuardInterceptor` | `kr.devslab.ssrfguard.restclient.SsrfGuardClientHttpRequestInterceptor` |
  | `kr.devslab.ssrfguard.security.SafeDnsResolver` | `kr.devslab.ssrfguard.httpclient5.SafeDnsResolver` |
  | `kr.devslab.ssrfguard.security.SafeRedirectStrategy` | `kr.devslab.ssrfguard.httpclient5.SafeRedirectStrategy` |
  | `kr.devslab.ssrfguard.security.NetUtil` | `kr.devslab.ssrfguard.core.NetUtil` |
- **`SecurityException` → `SsrfGuardException`.** 모든 거부 경로가 `SsrfGuardException`을 던짐 (여전히 `SecurityException` 서브클래스 — v2.0.0 catch 블록은 계속 동작). `BlockReason` enum 태그를 노출.
- **새 properties.** `ssrf.guard.reject-ip-literal-hosts`, `ssrf.guard.reject-user-info` 기본 `true` — 끄면 v2.0.0 동작으로 복원.

### Migration

대부분 **버전 올리고 재빌드**하면 끝:

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.0.0</version>
</dependency>
```

`ssrf-guard` 메타 아티팩트가 `-core`, `-httpclient5`, `-restclient`를 transitive로 끌어와 v2.0.0 전체 surface 제공.

다른 HTTP 클라이언트를 쓰면 해당 모듈 선택:

```xml
<!-- RestTemplate -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-resttemplate</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- WebClient -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-webclient</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Spring AI 툴 콜 -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard-springai</artifactId>
    <version>3.0.0</version>
</dependency>
```

외부 호출에서 `SecurityException` catch 하던 코드는 그대로 동작 — `SsrfGuardException extends SecurityException`. 구조화 태그가 필요하면 `SsrfGuardException` catch 후 `e.reason()` 검사.

## [2.0.0] — `kr.devslab:ssrf-guard`로 리브랜딩

### Changed

- **BREAKING — 좌표 변경.** `com.devs.lab:ssrf-guard-spring-boot-starter` → `kr.devslab:ssrf-guard`. 레거시 아티팩트는 Maven Central에 발행된 적 없으므로 v2.0.0이 첫 Central 공식 릴리즈.
- **BREAKING — 패키지 이름 변경.** `devs.lab.ssrf.*` → `kr.devslab.ssrfguard.*`:

  | Old | New |
  | --- | --- |
  | `devs.lab.ssrf.config.SsrfGuardAutoConfiguration` | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardAutoConfiguration` |
  | `devs.lab.ssrf.security.SsrfGuardProperties` | `kr.devslab.ssrfguard.autoconfigure.SsrfGuardProperties` |
  | `devs.lab.ssrf.security.SsrfGuardInterceptor` | `kr.devslab.ssrfguard.security.SsrfGuardInterceptor` |
  | `devs.lab.ssrf.security.SafeDnsResolver` | `kr.devslab.ssrfguard.security.SafeDnsResolver` |
  | `devs.lab.ssrf.security.SafeRedirectStrategy` | `kr.devslab.ssrfguard.security.SafeRedirectStrategy` |
  | `devs.lab.ssrf.security.NetUtil` | `kr.devslab.ssrfguard.security.NetUtil` |

- **BREAKING — `SsrfGuardApplication` 제거.** 원래 Spring Initializr 템플릿에서 남은 빈 `@SpringBootApplication`; 라이브러리 starter가 main class를 가질 이유 없음.
- **빌드 시스템: Maven → Gradle 8.10** + Vanniktech maven-publish 0.30.0. easy-paging-spring-boot-starter, api-log와 같은 컨벤션.
- **릴리즈 흐름: semantic-release → tag-triggered Gradle publish.** `v[0-9]+.[0-9]+.[0-9]+` 매치 git tag가 release workflow 실행, 한 단계에 build + sign + Sonatype Central Portal 업로드 + GitHub Release 생성.

### Added

- **CI 워크플로** (`.github/workflows/ci.yml`) — `main` push, PR마다 `./gradlew build jacocoTestReport` 실행, Codecov에 커버리지 업로드.
- **문서 사이트** https://ssrf-guard.devslab.kr/ — 설치, 빠른 시작, 보안 모델, 설정 레퍼런스. mkdocs-material + i18n (영문 + 한국어).
- **이중 언어 README** (`README.md` / `README.ko.md`).
- **모든 방어 동작에 대한 풀 테스트 커버리지**:
  - `NetUtilTest` — whitelist 매칭 (exact + suffix), IDN 정규화, IPv4 사설 IP 분류 (loopback, RFC-1918, link-local 포함 AWS 메타데이터, CGNAT, benchmark, broadcast) + IPv6 (ULA, link-local).
  - `SafeDnsResolverTest` — whitelist 게이트 + 사설 IP 필터, "필터 후 남는 게 없음" 경로 포함.
  - `SsrfGuardInterceptorTest` — 스킴/호스트/포트 accept/reject 매트릭스, suffix 라벨 경계 lookalike (전형적 `badexample.com` 우회).
  - `SsrfGuardAutoConfigurationTest` — 활성화 시 모든 public 빈 등록, `ssrf.guard.enabled=false`일 때 등록 안 됨.
  - `SsrfGuardIntegrationTest` — `MockWebServer`를 통한 실제 HTTP, 4-레이어 방어 end-to-end.

### Migration

의존성 좌표 및 직접 import 업데이트:

```xml
<!-- v1.x (Maven Central에 없음) -->
<dependency>
    <groupId>com.devs.lab</groupId>
    <artifactId>ssrf-guard-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- v2.0.0 -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

`application.yml` 키는 변경 없음 — `ssrf.guard.*` 그대로 동작.

보안 타입 직접 import 사용 중? `devs.lab.ssrf`를 `kr.devslab.ssrfguard`로 바꾸고 `kr.devslab.ssrfguard.autoconfigure` (properties + auto-config) 와 `kr.devslab.ssrfguard.security` (interceptor + resolver + redirect + NetUtil) 로 split.

## [1.1.0] — 2025-09-23

pre-v2 작업의 semantic-release rollup. tag만 있고 **Maven Central에 발행 안 됨**.

- README + releaser 템플릿 정리

## [1.0.0] — 2025-09-23

첫 공개 릴리즈. tag만 있고 **Maven Central에 발행 안 됨**.

- `com.devs.lab:ssrf-guard-spring-boot-starter` 첫 cut
