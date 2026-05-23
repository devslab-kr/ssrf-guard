# 설치

## 요구사항

- **Java 21+**
- **Spring Boot 3.5+** (Spring 기반 모듈)
- Spring AI 모듈: **Spring AI 1.0+**
- Feign 모듈: **Spring Cloud 2024.0+**

## 모듈 선택

ssrf-guard v3.0.0은 HTTP 클라이언트 경계로 분리되어 있습니다. 실제로 쓰는 모듈만 고르면 됩니다.

=== "RestClient (Spring Boot 3.x 기본)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    메타 아티팩트가 `-core`, `-httpclient5`, `-restclient`를 끌어옵니다 — v2.0.0 전체 surface와 동등.

=== "RestTemplate"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-resttemplate</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    `RestTemplateBuilder`에 동일한 `ClientHttpRequestInterceptor` + `HttpComponentsClientHttpRequestFactory`를 wiring. 둘 다 쓰면 `-restclient`도 추가.

=== "WebClient (WebFlux)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-webclient</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    자동설정된 `WebClient.Builder`에 `ExchangeFilterFunction` 추가. Reactive — 정책 위반은 `Mono.error(SsrfGuardException)`으로 옴.

=== "Feign"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-feign</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    `feign.RequestInterceptor` 등록 — Spring Cloud OpenFeign이 모든 `@FeignClient`에 자동 적용.

=== "Spring AI 툴 콜"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-springai</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    모든 `ToolCallback` 빈을 URL 인자 검증으로 감쌈. **LLM 에이전트가 URL을 받아 fetch하는 시나리오의 결정적인 새 SSRF 표면.**

=== "JDK HttpClient (Spring 없음)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-jdkhttp</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    직접 사용:
    ```java
    HttpClient safe = new SsrfGuardedHttpClient(HttpClient.newHttpClient(), urlPolicy);
    ```

=== "OkHttp (Spring 없음)"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>ssrf-guard-okhttp</artifactId>
        <version>3.0.0</version>
    </dependency>
    ```

    ```java
    OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(new SsrfGuardOkHttpInterceptor(urlPolicy))
        .dns(new SsrfGuardOkHttpDns(hostPolicy, true))
        .build();
    ```

!!! tip "최신 버전"
    `3.0.0`을 [Maven Central](https://central.sonatype.com/artifact/kr.devslab/ssrf-guard) 최신으로 교체.

!!! info "v2.0.0에서 올라온다면"
    `kr.devslab:ssrf-guard` 좌표는 여전히 동작 — 메타 아티팩트가 `-core`, `-httpclient5`, `-restclient`를 끌어옴. 대부분 버전만 올리고 다시 빌드하면 끝. `kr.devslab.ssrfguard.security.*` 직접 import한 코드는 [v3.0.0 changelog](../changelog.md#300--multi-module--llm-agent-ssrf-defense)의 패키지 매핑 참고.

## 최소 설정

어떤 모듈이든 `ssrf.guard.*` 키는 동일:

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true                    # 기본값 — false로 끄면 가드 비활성화
    suffixes:                        # 최소한 이거 — 비어있으면 "모든 호스트 차단"
      - api.partner.com
      - example.org
```

화이트리스트 비워두면 **fail-closed** — 모든 외부 호출이 `Host not allowed`로 막힘. 의도된 동작입니다.

## 추가 하드닝 (기본값이 안전)

```yaml
ssrf:
  guard:
    reject-ip-literal-hosts: true    # 기본 — http://127.0.0.1, http://2130706433 등 차단
    reject-user-info: true           # 기본 — https://user:pass@host/... 차단
    block-private-networks: true     # 기본 — DNS 단계 사설/메타데이터 IP 필터
    follow-redirects: true           # 기본 — 매 hop 재검증
    allowed-schemes: [https]         # 기본 [http, https]보다 엄격
    allowed-ports: [443]             # 기본 [-1, 80, 443]보다 엄격
```

## 관찰성 (선택)

`spring-boot-starter-actuator`를 추가하면 Micrometer 메트릭이 무료:

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

태그가 bounded (`reason`은 enum, `scheme`은 http/https) 라서 Prometheus / Datadog / CloudWatch에 문제 없이 적재됨.

## 설치 확인

Spring 모듈은 `--debug` 출력에 자동설정 이름이 보임:

```bash
./gradlew bootRun --args='--debug' | grep -E 'SsrfGuard.*AutoConfiguration'
```

`matched:` 라인이 최소 하나 보여야 정상. 의도적으로 차단되는 URL을 호출하면:

```
WARN  k.d.s.core.UrlPolicy : ssrf-guard: Host not allowed: evil.com (reason=blocked_host, scheme=https, host=evil.com)
```

다음은 [빠른 시작](quickstart.md)에서 실제 파트너 API로 동작 확인하기, 또는 위협 모델 전체는 [보안 모델](../guides/security-model.md).
