# 변경 이력

ssrf-guard의 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 본 프로젝트는 [Semantic Versioning](https://semver.org/lang/ko/)을 준수합니다.

## [Unreleased]

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
