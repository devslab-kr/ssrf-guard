# ssrf-guard

[English](README.md) · **한국어**

> Spring Boot용 SSRF(Server-Side Request Forgery) 방어 — 화이트리스트 기반 outbound HTTP 가드. 사설망 차단, 리다이렉트 재검증, TOCTOU 완화 포함.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/ssrf-guard.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard)
[![CI](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/ssrf-guard/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/ssrf-guard/branch/main/graph/badge.svg)](https://codecov.io/gh/devslab-kr/ssrf-guard)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[문서 → ssrf-guard.devslab.kr](https://ssrf-guard.devslab.kr/ko/)**

## 무엇을 하나

Spring Boot 서비스의 모든 outbound HTTP 호출이 소켓 열리기 전 4단계 SSRF 필터를 통과합니다:

1. **스킴 / 호스트 / 포트** — 문자열 레벨 인터셉터가 허용 리스트 밖이면 reject, DNS 아직 안 함
2. **DNS 시점 화이트리스트 재검증** — 호스트 정책을 한 번 더 적용, 위조된 URL이 빠져나갈 여지 차단
3. **사설망 IP 필터** — loopback (`127.0.0.0/8`, `::1`), RFC-1918 (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`, `fe80::/10`, AWS 메타데이터 엔드포인트 포함), CGNAT (`100.64/10`), IPv6 ULA (`fc00::/7`) 모두 차단
4. **리다이렉트 재검증** — 3xx 홉마다 동일한 스킴/호스트/IP 룰 적용. 공격자가 `example.com` 화이트리스트 후 `169.254.169.254`로 redirect 못 시킴

resolver가 검증한 동일 `InetAddress` 배열이 HttpClient의 `Socket.connect()`에 전달됨 — TOCTOU 윈도우 닫힘.

## 설치

=== Maven

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

=== Gradle (Kotlin DSL)

```kotlin
implementation("kr.devslab:ssrf-guard:2.0.0")
```

> **`com.devs.lab:ssrf-guard-spring-boot-starter` 1.x에서 업그레이드?** 좌표, 패키지, 최소 Spring Boot 버전 모두 v2.0.0에서 바뀌었습니다 — [v2.0.0 changelog](CHANGELOG.md#200--rebrand-to-krdevslabssrf-guard) 참고.

## 설정

```yaml
ssrf:
  guard:
    enabled: true                          # 마스터 스위치
    allowed-schemes: [ "http", "https" ]
    allowed-ports:  [ -1, 80, 443 ]        # -1 = 스킴 기본 포트
    block-private-networks: true
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
        // SecurityException 발생.
        return client.get()
                .uri("https://api.partner.com/customers/{id}", id)
                .retrieve()
                .body(Customer.class);
    }
}
```

화이트리스트 아닐 때:

```text
java.lang.SecurityException: Host not allowed: evil.com
    at kr.devslab.ssrfguard.security.SsrfGuardInterceptor.intercept(...)
```

## 자동 구성이 등록하는 빈

`ssrf.guard.enabled=true`(기본값)이면 `SsrfGuardAutoConfiguration`이 활성화되어 다음을 등록:

- `SafeDnsResolver` — 화이트리스트 + 사설 IP 필터. Apache HttpClient 5 connection manager에 연결
- `CloseableHttpClient` — resolver와 (리다이렉트 활성 시) `SafeRedirectStrategy` 와이어업
- `HttpComponentsClientHttpRequestFactory` — connect/read timeout 적용
- `SsrfGuardInterceptor` — 프론트라인 스킴/호스트/포트 체크
- `RestClientCustomizer` (`ssrfRestClientCustomizer`) — factory + interceptor를 Spring Boot 자동 `RestClient.Builder`에 핀

모든 빈이 `@ConditionalOnMissingBean` — 일부만 직접 정의해서 교체 가능 (예: 인증 헤더 포함 `CloseableHttpClient` 제공하면서 다른 SSRF 정책은 그대로 사용).

## 요구사항

- Java 21+
- Spring Boot 3.5+
- Apache HttpClient 5 (transitive)

## 라이선스

Apache License 2.0 — [LICENSE](LICENSE), [NOTICE](NOTICE) 참고.

---

[Devslab](https://devslab.kr) 제작 · [DevsLab 오픈소스 툴킷](https://github.com/devslab-kr) 일부.
