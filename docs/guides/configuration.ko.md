# 설정

`ssrf.guard.*` 의 모든 옵션 + 기본값 + 변경 시점.

## 마스터 스위치

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.enabled` | `true` | `false`로 두면 스타터 전체 비활성. 빈 등록 안 함; Spring Boot 기본 `RestClient.Builder`가 outbound 트래픽을 그대로 처리. |

## 화이트리스트

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.exact-hosts` | `[]` | 정확 일치 호스트 화이트리스트. case-insensitive + IDN 정규화. |
| `ssrf.guard.suffixes` | `[]` | 서픽스 매치 화이트리스트. `example.com`은 자기 자신 **및** 라벨 경계의 서브도메인 (`api.example.com`, `a.b.example.com`) 매치 — `badexample.com`은 매치 안 됨. |

둘 다 default empty. 비어 있으면 **모든 outbound 호출이 `Host not allowed`로 거부** — fail-closed. 가드가 무엇이라도 허용하려면 최소 하나의 entry 필요.

```yaml
ssrf:
  guard:
    exact-hosts:
      - api.partner.com         # 그 정확한 이름만
    suffixes:
      - partner-internal.com    # partner-internal.com과 모든 서브도메인
```

## 스킴 / 포트

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.allowed-schemes` | `["http", "https"]` | 허용 URL 스킴. `["https"]`로 두면 plain HTTP 금지. |
| `ssrf.guard.allowed-ports` | `[-1, 80, 443]` | 허용 TCP 포트. **`-1`은 "URI가 명시 포트 생략, 스킴 default 적용"** — 이걸 두는 게 `https://api.example.com/` (명시 `:443` 없음) 일반적인 형태 허용. |

```yaml
ssrf:
  guard:
    allowed-schemes: [ "https" ]    # https 만
    allowed-ports:   [ -1, 443 ]    # 기본 포트 또는 명시 443
```

## 사설망 차단

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.block-private-networks` | `true` | `true`면 DNS resolver가 반환하는 모든 IP가 public routable — loopback, RFC-1918, link-local, CGNAT, multicast, broadcast, IPv6 ULA 모두 차단. 전체 목록은 [보안 모델](security-model.md#block-private-networks가-차단하는-것) 참고. |

internal 호출이 의도적으로 필요할 때만 끄기 (예: private subnet의 서비스 간 통신). 실수로 SSRF를 재활성화시키는 가장 흔한 config knob — 변경마다 review.

## 리다이렉트 처리

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.follow-redirects` | `true` | `true`면 3xx 홉마다 동일 스킴 + DNS-resolver 정책 재검증. `false`면 클라이언트가 리다이렉트를 caller에 그대로 반환하고 거기서 종료. |

리다이렉트 비활성은 가끔 belt-and-suspenders 보안으로 사용되지만 HTTP→HTTPS 업그레이드를 위한 `301 Moved Permanently`를 의존하는 partner 통합을 깨는 경우 많음.

## 타임아웃

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.connect-timeout` | `5s` | 소켓 connect timeout. 표준 Spring `Duration` 파싱 (`5s`, `500ms`, `PT5S` 등). |
| `ssrf.guard.read-timeout` | `10s` | 소켓 read timeout. |

기본값이 의도적으로 보수적 — 타임아웃이 unbounded면 느린 partner API가 denial-of-service 벡터가 될 수 있음. 필요한 만큼만 늘림.

## 예약됨

| 속성 | 기본값 | 비고 |
|---|---|---|
| `ssrf.guard.enable-additional-dns-caching` | `false` | 향후 사용 예약. 현재 효과 없음 — 모든 요청이 `SafeDnsResolver`로 DNS 해석하고 JVM-wide `networkaddress.cache.ttl`이 caching 제어. |

## 전체 예시

```yaml title="application.yml"
ssrf:
  guard:
    enabled: true
    allowed-schemes: [ "https" ]
    allowed-ports: [ -1, 443 ]
    block-private-networks: true
    follow-redirects: true
    connect-timeout: 5s
    read-timeout: 10s
    exact-hosts:
      - api.stripe.com
      - hooks.slack.com
    suffixes:
      - partner.example.com
      - googleapis.com
```

이 정도 화이트리스트면 partner-integration이 많은 거의 모든 서비스를 SSRF에서 보호.

## LLM 어댑터 properties (v3.1+)

LLM 툴 빈 자동 wrap을 제어하는 두 토글. 둘 다 기본 `true` — 자체 코드에서 (`SsrfGuardedToolCallbacks.wrap(...)` / `SsrfGuardedToolExecutors.wrap(...)`) 선별적으로 wrap하고 싶으면 `false`로 끔.

| 키 | 기본값 | 효과 |
|---|---|---|
| `ssrf.guard.springai.wrap-tool-callbacks` | `true` | Spring AI 모든 `ToolCallback` 빈 자동 wrap. `ssrf-guard-springai` classpath 필요. |
| `ssrf.guard.langchain4j.wrap-tool-executors` | `true` | LangChain4j 모든 `ToolExecutor` 빈 자동 wrap. `ssrf-guard-langchain4j` classpath 필요. |

```yaml
ssrf:
  guard:
    enabled: true
    exact-hosts:
      - api.partner.com
    springai:
      wrap-tool-callbacks: true     # 기본
    langchain4j:
      wrap-tool-executors: true     # 기본
```
