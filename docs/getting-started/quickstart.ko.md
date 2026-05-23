# 빠른 시작

5분 워크스루: ssrf-guard 설치 → 화이트리스트 설정 → 실제 파트너 API 호출 → 악성 URL이 차단되는 것 확인.

## 1. 의존성 추가

```kotlin title="build.gradle.kts"
implementation("kr.devslab:ssrf-guard:2.0.0")
```

## 2. 화이트리스트 설정

```yaml title="application.yml"
ssrf:
  guard:
    suffixes:
      - api.partner.com
```

이게 유일한 필수 설정 — 스킴, 포트, 사설망 차단, 리다이렉트 재검증은 기본값으로 처리됩니다.

## 3. `RestClient` 주입

```java title="PartnerApiService.java"
package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PartnerApiService {

    private final RestClient client;

    public PartnerApiService(RestClient.Builder builder) {
        // builder가 이미 ssrf-guard에 의해 customize됨 — 여기서 만든
        // RestClient는 자동으로 SSRF 정책 적용.
        this.client = builder.build();
    }

    public String fetchCustomer(long id) {
        return client.get()
                .uri("https://api.partner.com/customers/{id}", id)
                .retrieve()
                .body(String.class);
    }
}
```

## 4. 차단 URL 시도

같은 `RestClient`, 다른 호스트:

```java
String body = client.get()
        .uri("http://169.254.169.254/latest/meta-data/")
        .retrieve()
        .body(String.class);
```

```text
java.lang.SecurityException: Host not allowed: 169.254.169.254
    at kr.devslab.ssrfguard.security.SsrfGuardInterceptor.intercept(SsrfGuardInterceptor.java:48)
```

인터셉터가 DNS 전, 연결 전, 무엇이든 잘못될 수 있는 어떤 것 전에 거부했습니다. 어떻게든 인터셉터를 우회한다면 (parser가 다르게 처리하는 URL 형식 같은), `SafeDnsResolver`가 DNS 시점에 잡아서 loopback / link-local IP 반환을 거부합니다.

## 5. 리다이렉트 공격 시도

`302 Location: http://169.254.169.254/...`를 반환하는 호스트를 화이트리스트에 추가:

```yaml title="application.yml"
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - your-redirect-host.com
```

```java
client.get()
        .uri("https://your-redirect-host.com/redirect-to-metadata")
        .retrieve()
        .body(String.class);
```

```text
org.apache.hc.client5.http.RedirectException: Blocked redirect to host: 169.254.169.254 cause: ...
```

`SafeRedirectStrategy`가 리다이렉트 타겟을 link-local 차단 DNS resolver에 다시 통과시켰습니다. 공격자의 미끼가 `169.254.169.254` 직접 호출과 정확히 같은 정도 — 즉 전혀 통하지 않음 — 으로 동작했습니다.

## 방금 일어난 일

```
RestClient.get(uri)
   ↓
SsrfGuardInterceptor   ← 스킴 / 호스트 / 포트 화이트리스트 (DNS 아직)
   ↓
HttpComponents → SafeDnsResolver   ← 화이트리스트 + 사설 IP 필터
   ↓
Socket.connect(resolved-addr)   ← 동일 InetAddress 배열, 재조회 없음
   ↓
(3xx 시)
SafeRedirectStrategy   ← 타겟 URI를 동일 체크에 통과
   ↓
loop 또는 terminate
```

3개 독립 게이트, 각각 공격자가 시도할 다른 우회를 차단.

## 더 읽어볼 거리

- [보안 모델](../guides/security-model.md) — 각 레이어의 보장과 한계
- [설정](../guides/configuration.md) — 모든 속성, 기본값, 변경 시점
- [변경 이력](../changelog.md) — 레거시 `com.devs.lab` 좌표에서 v2.0.0 마이그레이션 매핑
