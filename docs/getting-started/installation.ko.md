# 설치

## 요구사항

- **Java 21+**
- **Spring Boot 3.5+**

## 의존성 추가

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard</artifactId>
        <version>2.0.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:ssrf-guard:2.0.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:ssrf-guard:2.0.0'
    }
    ```

!!! tip "최신 버전"
    `2.0.0`은 [Maven Central](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard)의 최신 버전으로 교체.

!!! info "1.x에서 오는 경우?"
    레거시 `com.devs.lab:ssrf-guard-spring-boot-starter`는 Maven Central에 발행된 적 없습니다. v2.0.0이 `kr.devslab:ssrf-guard`로의 첫 정식 릴리즈. 마이그레이션 매핑은 [v2.0.0 변경 이력](../changelog.md) 참고.

## ssrf-guard가 가져오는 의존성

- `spring-boot-starter` (transitive)
- `spring-boot-autoconfigure`
- `org.apache.httpcomponents.client5:httpclient5` — 래핑된 `RestClient`의 백엔드 HTTP 스택

Spring Web (`spring-boot-starter-web`)은 compile-time에는 **옵셔널**이지만 런타임에는 필요 — 자동 구성이 `@ConditionalOnClass(RestClient.class)`로 게이팅됩니다. `RestClient`를 전혀 사용하지 않는 pure-WebFlux 애플리케이션에서는 가드가 활성화되지 않습니다.

## 직접 제공할 것

없습니다. ssrf-guard는 Spring Boot 기본 `RestClient.Builder`에 자기 자신을 핀(pin)하는 자동 구성을 제공하므로, 코드에서 만드는 모든 `RestClient`가 SSRF 정책을 자동으로 picks up합니다.

다만 `application.yml`에 다음을 설정하는 게 일반적:

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true                # 기본값 — false면 완전 opt-out
    suffixes:                    # 최소한 이거 — 비어 있으면 "모두 차단"
      - api.partner.com
      - example.org
```

`ssrf.guard.*` 설정 없는 consumer는 가드가 활성화되지만 **whitelist 호스트 없음** — 즉 모든 outbound 호출이 `Host not allowed`로 실패. 의도된 fail-closed 동작이지만 알아둘 가치 있음.

## 자동 구성이 하는 일

`ssrf.guard.enabled`가 `true` (기본값)이고 `RestClient`가 classpath에 있으면 `SsrfGuardAutoConfiguration`이 활성화되어 다음을 등록:

- `SafeDnsResolver` — DNS 시점에 화이트리스트 재적용 + 사설/loopback/link-local/multicast IP 필터
- `CloseableHttpClient` — Apache HttpClient 5 기반, resolver 연결 + (리다이렉트 활성 시) `SafeRedirectStrategy`로 매 hop 재검증
- `HttpComponentsClientHttpRequestFactory` — 설정된 connect/read timeout 적용
- `SsrfGuardInterceptor` — DNS 전 스킴/호스트/포트 프론트라인 체크
- `ssrfRestClientCustomizer` — factory + interceptor를 Spring Boot 자동 `RestClient.Builder`에 핀하는 `RestClientCustomizer`

모든 빈이 `@ConditionalOnMissingBean`. 직접 빈을 정의하면 오버라이드됩니다.

## 설치 확인

앱 시작 후 자동 구성 빈이 컨텍스트에 있는지 확인:

```bash
./gradlew bootRun --args='--debug' | grep SsrfGuardAutoConfiguration
```

`SsrfGuardAutoConfiguration matched: ...` 같은 줄이 보여야 합니다. 의도적으로 차단된 URL을 시도하면 로그에서 `SecurityException: Host not allowed: ...`를 보게 됩니다.

실제 partner API에 대한 가드 동작을 보려면 [빠른 시작](quickstart.md)으로 이동.
