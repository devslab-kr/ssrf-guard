# 보안 모델

ssrf-guard는 4개의 독립적인 게이트로, 각각 다른 SSRF 우회를 차단합니다. 각 게이트가 무엇을 보장하고 어디서 한계가 있으며 무엇을 추가로 신경 써야 하는지 정리.

## 4개 게이트

| 게이트 | 시점 | 검증 | 실패 모드 |
|---|---|---|---|
| **`SsrfGuardInterceptor`** | DNS 전 | URL 스킴, 호스트 (whitelist 매치), 포트 | `SecurityException` |
| **`SafeDnsResolver`** (whitelist) | DNS 해석 시점 | 호스트 (whitelist 재매치) | `UnknownHostException` |
| **`SafeDnsResolver`** (IP 필터) | DNS 해석 시점 | 각 IP가 사설/loopback/link-local/multicast/CGNAT/benchmark/IPv6-ULA 아님 | 필터 후 남는 게 없으면 `UnknownHostException` |
| **`SafeRedirectStrategy`** | 모든 3xx에서 | 리다이렉트 타겟에 대해 스킴 + DNS resolver 재실행 | `RedirectException` |

게이트는 다른 게이트가 통과했더라도 각자 실행 — defense in depth. 공격자는 모든 레이어를 우회해야 outbound 호출에 성공.

## 화이트리스트를 두 번 체크하는 이유

순진한 "URL 한 번 체크하고 요청" 패턴에는 race condition이 있음:

1. 앱이 URL 파싱, 호스트 추출
2. 앱이 호스트 문자열로 화이트리스트 체크
3. 앱이 URL을 HTTP 클라이언트에 전달
4. HTTP 클라이언트가 DNS 해석 — 호스트가 의미하는 IP와 다른 IP를 받음
5. 그 다른 IP로 연결

(2)와 (4) 사이에 URL 문자열의 의미가 변함. ssrf-guard는 두 가지 방식으로 해결:

- **DNS 시점에 화이트리스트를 다시 체크** (`SafeDnsResolver.resolve()`) — resolver가 보는 호스트가 인터셉터가 통과시킨 호스트와 동일해야 함
- **반환된 `InetAddress[]`가 HttpClient의 `Socket.connect()`에 직접 전달** — 해석과 연결 사이에 DNS 재조회 없음. resolver가 검증한 IP가 소켓이 여는 IP

이게 프로젝트 description의 "TOCTOU mitigation" 라인.

## `block-private-networks`가 차단하는 것

기본 `true`. 다음 중 하나에 매치되는 해석:

- **Loopback**: `127.0.0.0/8`, `::1`
- **RFC 1918**: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
- **Link-local**: `169.254.0.0/16` (`169.254.169.254` AWS 메타데이터 포함), `fe80::/10`
- **CGNAT**: `100.64.0.0/10`
- **Benchmark**: `198.18.0.0/15`
- **Multicast**: `224.0.0.0/4`, `ff00::/8`
- **IPv6 ULA**: `fc00::/7`
- **Any-local**: `0.0.0.0`, `::`
- **Broadcast**: `255.255.255.255`

Java 내장 `InetAddress.isSiteLocalAddress()`는 CGNAT, benchmark range, IPv6 카테고리 대부분을 놓치므로 `NetUtil.isPrivateOrLocal()`을 직접 작성해서 모두 커버.

## ssrf-guard가 **하지 않는** 것

솔직한 한계 목록. 경계 인식이 threat model의 일부.

- **HTTP 클라이언트마다 다른 URL 파싱을 검증하지 않음.** JDK `URI` 생성자, Spring `UriComponentsBuilder`, Apache HttpClient의 request line — `://user:pass@a.com\@b.com/` 같은 문자열이 어떤 호스트인지 항상 동의하지 않음. 신뢰할 수 없는 입력에서 URL을 받으면 `RestClient`에 넘기기 전 정규화해야. OWASP cheat sheet의 [URL parser confusion section](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html#network-layer) 권장.
- **wrap되지 않은 HTTP 클라이언트는 보호 안 함.** 자동 구성된 `RestClient.Builder` / `RestTemplateBuilder` / `WebClient.Builder` / `OkHttpClient.Builder` 등의 경로를 거치지 않고 직접 만든 `HttpURLConnection` 같은 코드는 SSRF 정책 우회. v3.1+는 주요 Java HTTP 스택 (RestClient · RestTemplate · WebClient · Feign · OkHttp · JDK `HttpClient` · Apache HttpClient 5)과 LLM 툴 dispatch (Spring AI · LangChain4j)를 모두 커버 — 사용 중인 각각에 맞는 모듈을 골라 추가.
- **WebClient는 v3.1부터 URL 단계 *+* DNS 단계 방어 모두.** v3.0.x WebClient 모듈은 URL 단계 필터만 실행 — 화이트리스트를 통과한 호스트가 사설 IP로 resolve되어도 reactor-netty가 그대로 연결. v3.1의 `SsrfGuardReactorAddressResolverGroup`이 reactor-netty의 address resolver에 후킹해서 다른 모듈과 동일한 사설/loopback 범위로 필터링. 비-Netty WebFlux 백엔드 (Jetty Reactive, Helidon)도 URL 단계 방어는 받음; connector 교체는 reactor-netty classpath 의존.
- **JVM 캐시가 작용할 때 DNS rebinding을 막지 않음.** Java가 DNS 해석을 캐시; JVM이 영구 캐싱 (Java 8u192 이전 보안 정책 default)이면 hostname의 record 변경 후에도 캐시된 IP를 계속 hit. 모던 JVM은 default가 30초이긴 함 — `networkaddress.cache.ttl` 합리적 값 유지.
- **`exact-hosts`에 사설 IP literal을 직접 넣는 걸 막지 않음.** `10.0.0.5`를 화이트리스트하면 인터셉터가 호스트를 통과시키고, DNS resolver가 그 IP로 단락. (단, `block-private-networks=false`로 안 두면) 사설 IP 필터가 여전히 적용되어 요청은 거부 — 하지만 레이어링이 "인터셉터 통과, resolver 거부"이지 "인터셉터 즉시 거부" 아님.
- **응답 본문 검증 안 함.** 화이트리스트 호스트라도 downstream 이슈를 트리거하는 콘텐츠 반환 가능. SSRF 방어는 신뢰하는 호스트로 소켓이 연결될 때 끝남; 그 호스트가 반환하는 건 애플리케이션 로직 책임.

## Threat-model 체크리스트

ssrf-guard 사용 시 추가로 신경 쓸 것:

1. **`block-private-networks=true`로 운영** — 특정 이유로 내부 호출 허용해야 하는 게 아니라면. default가 `true`인 이유가 정확히 이거.
2. **`follow-redirects=true` 유지** — 특정 이유로 리다이렉트 금지해야 하는 게 아니라면. 리다이렉트 비활성은 가끔 defense in depth지만 정상 API 통합을 깨는 경우 많음.
3. **화이트리스트를 보안 중요 설정으로 취급.** 거기 쓸 수 있는 사람은 사실상 ssrf-guard를 우회 가능. 설정 변경은 코드 리뷰 통과.
4. **URL이 user input과 string concat되지 않게.** ssrf-guard가 active여도 `https://api.partner.com/proxy?target=` + user-supplied URL은 자체 SSRF (당신이 공격자의 proxy가 됨). composing 전 user-supplied URL 검증.
5. **로그에서 `SecurityException: Host not allowed` 모니터링.** 공격자 probe이거나 whitelist update가 필요한 정상 통합이거나.

OWASP [SSRF prevention cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)는 6개월에 한 번 re-read 가치 있음.
