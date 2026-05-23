# ssrf-guard

> JVM용 SSRF(Server-Side Request Forgery) 방어 라이브러리 — 화이트리스트 기반 외부 HTTP 가드, 사설 네트워크 차단, 리다이렉트 재검증, TOCTOU 방어, **Spring AI LLM 에이전트 툴 URL 검증** 포함.

## 왜 필요한가

JVM 서비스가 사용자에게서 URL을 받아 처리하는 모든 곳 — 웹훅 콜백, 이미지 fetch 헬퍼, "URL에서 가져오기" 기능, **LLM 에이전트의 `fetch_url` 툴**, 문자열을 받아 HTTP 호출로 바꾸는 모든 코드 — 는 한 번의 fetch만으로 `http://169.254.169.254/latest/meta-data/`를 통한 AWS 자격증명 유출, 사내망 스캔, 루프백에만 노출된 어드민 엔드포인트 호출로 이어질 수 있습니다. SSRF가 [OWASP Top 10 2021 — A10](https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/)에 오른 게 정확히 이런 "서버는 자기 자신을 기본 신뢰한다" 류의 실패 때문이고, **2024-2025 LLM 에이전트 붐 이후 더더욱 중요해졌습니다.**

ssrf-guard는 작은 core(정책 + IP 분류) + HTTP 클라이언트별 모듈로 구성돼 어떤 스택을 쓰든 그대로 드롭인 가능합니다.

## 모듈 매트릭스

쓰는 HTTP 클라이언트에 맞는 모듈만 고르세요. core는 transitive로 따라옵니다.

| 모듈 | 대상 | Spring 필요? |
|---|---|---|
| **`ssrf-guard`** | 메타 아티팩트 — RestClient + HttpClient5 (v2.0.0 호환) | Yes |
| `ssrf-guard-restclient` | Spring 6.1+ `RestClient` | Yes |
| `ssrf-guard-resttemplate` | Spring `RestTemplate` (엔터프라이즈/레거시) | Yes |
| `ssrf-guard-webclient` | Spring WebFlux `WebClient` — URL 단계 필터 **+** reactor-netty DNS 단계 IP 필터 (v3.1+) | Yes (WebFlux) |
| `ssrf-guard-feign` | Spring Cloud OpenFeign | Yes (Cloud) |
| `ssrf-guard-llm` 🧩 | 프레임워크-중립 JSON 툴 입력 검증 (v3.1+). springai / langchain4j 어댑터가 사용; 커스텀 dispatcher에서도 직접 사용 가능. | No |
| **`ssrf-guard-springai`** ⭐ | Spring AI `ToolCallback` — LLM 에이전트 SSRF 차단 (`-llm` 위의 thin adapter) | Yes (AI) |
| **`ssrf-guard-langchain4j`** ⭐ | LangChain4j `ToolExecutor` — Java LLM 양대 프레임워크 다른 한쪽 (v3.1+, `-llm` 위의 thin adapter) | Yes |
| `ssrf-guard-httpclient5` | Apache HttpClient 5 직접 사용 | No |
| `ssrf-guard-jdkhttp` | `java.net.http.HttpClient` (JDK 11+) | No |
| `ssrf-guard-okhttp` | OkHttp | No |

## 4단계 방어

1. **URL 단계 (최전선)** — 스킴 + 호스트 + 포트 + IP 리터럴 형식 + userinfo 체크, DNS 조회 전. 난독화된 IP 우회 (`http://2130706433/` → `127.0.0.1`)를 가장 저렴한 단계에서 차단.
2. **DNS 단계 화이트리스트 재검증** — 실제 호스트 이름이 해석될 때 호스트 정책 한 번 더 적용.
3. **사설 네트워크 IP 필터** — 루프백, RFC-1918, 링크로컬(AWS 메타데이터 `169.254.169.254` 포함), CGNAT, IPv6 ULA, IPv4-mapped IPv6 (`::ffff:10.0.0.5` 우회), 6to4 (`2002::/16`) 전부 차단.
4. **리다이렉트 재검증** — 모든 3xx hop이 동일 체크를 거침. 공격자가 `example.com`을 화이트리스트에 넣고 302로 `169.254.169.254`로 보내는 시나리오 방지.

DNS resolver가 검증하는 `InetAddress` 배열은 *정확히 같은* 배열이 Apache HttpClient의 `Socket.connect()`로 전달됨 — 검증과 연결 사이에 두 번째 DNS 조회가 없으므로 단순 화이트리스트가 못 막는 TOCTOU 윈도우가 닫힙니다.

## Spring AI 툴 콜 — 새로운 SSRF 표면

LLM 에이전트가 일상적으로 URL을 fetch:

```python
@tool
def fetch_url(url: str) -> str:
    return requests.get(url).text    # ← URL이 공격자 컨트롤되면 SSRF 한 줄
```

Java/Spring AI 등가물도 **똑같이 취약합니다.** `ssrf-guard-springai`가 어떤 `ToolCallback`이든 감싸 URL 형식의 인자를 정책 검증 후에만 실행되도록 막고, 거부 시 LLM이 해석하고 복구할 수 있는 구조화된 에러 문자열을 반환 (예외 throw가 아님).

```java
ToolCallback[] raw = ToolCallbacks.from(new MyTools());
ToolCallback[] safe = SsrfGuardedToolCallbacks.wrap(raw, urlPolicy);

ChatClient.create(chatModel)
    .prompt("example.com 홈페이지 요약해줘")
    .toolCallbacks(safe)
    .call().content();
```

`ssrf-guard-springai`가 클래스패스에 있으면 자동 설정이 모든 `@Bean ToolCallback`을 `BeanPostProcessor`로 자동 wrap합니다.

## 관찰성

`MeterRegistry`가 클래스패스에 있으면 자동으로:

```
ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
ssrf_guard_allowed_total{scheme="https"} 13042
```

차단마다 구조화된 WARN 로그:

```
WARN ssrf-guard: blocked DNS — all resolved IPs are private/loopback
       (host=evil.com, resolved=[evil.com/10.0.0.5])
```

## 빠른 설치

Spring Boot 3.5+ + `RestClient`:

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>3.0.0</version>
</dependency>
```

```yaml
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - example.org
```

전체 모듈 매트릭스는 [설치 가이드](getting-started/installation.md), 5분 워크스루는 [빠른 시작](getting-started/quickstart.md).

## 어디서 찾나

- **GitHub**: <https://github.com/devslab-kr/ssrf-guard>
- **Maven Central**: <https://central.sonatype.com/artifact/kr.devslab/ssrf-guard>
- **Docs**: <https://ssrf-guard.devslab.kr/>

## 라이선스

Apache License 2.0. [DevsLab 오픈소스 툴킷](https://github.com/devslab-kr)의 일부.
