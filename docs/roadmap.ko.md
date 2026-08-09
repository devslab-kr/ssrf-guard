# 로드맵

[English](roadmap.md)

무엇이 끝났고, 무엇이 대기 중이며, 무엇을 의도적으로 하지 않기로 했는지.
2026-08-09 신설.

자매: [`@devslab/ssrf-guard-js`](https://github.com/devslab-kr/ssrf-guard-js)가
같은 보안 모델을 JS/TS로, 자기 버전 라인으로 낸다 — 모델을 공유하지 릴리스
라인을 공유하지 않는다.

## 현재 상태

- **배포:** `kr.devslab:ssrf-guard` **3.3.0** (Maven Central, 2026-08-09)
- **라인:** Spring Boot 3 단일 라인(`3.x.y`). 라이브러리 major가 Spring Boot
  major를 따른다 — org
  [버전 정책](https://github.com/devslab-kr/.github/blob/main/.github/VERSIONING.md)
- **모듈:** core, llm, HTTP 클라이언트 어댑터 6종(RestTemplate·RestClient·
  WebClient·Feign·OkHttp·JdkHttp·HttpClient5), LLM 어댑터 2종(Spring AI·
  LangChain4j), 벤치마크
- **테스트:** 239개, 2026-08-09 기준 전부 통과
- **데모:** [devslab-examples](https://github.com/devslab-kr/devslab-examples)에 8종

## 다음

**이름이 붙은 작업 하나: OkHttp 리다이렉트 홉.**

3.3.0이 코어의 단일 정의([`RedirectGuard`](https://github.com/devslab-kr/ssrf-guard/blob/main/ssrf-guard-core/src/main/java/kr/devslab/ssrfguard/core/RedirectGuard.java))로
모든 홉에 첫 요청과 같은 검사를 주었다 — `jdkhttp`와 `httpclient5`에 대해서.
**OkHttp는 빠졌고**, 다시 시도하기 전에 이유를 읽을 값어치가 있다:

- `Dns` 계층이 홉마다 호스트 허용 목록과 사설 IP는 여전히 재검사한다. 그래서
  좁은 잔여 위험은 **허용된 같은 호스트**의 다른 포트·스킴이거나 userinfo가
  붙은 경우이지, 내부 주소로 가는 경로가 아니다.
- **network interceptor는 그 이음매가 아니다.** OkHttp는 그걸 **연결이 맺어진
  뒤에** 호출한다. 메타데이터 주소를 상대로 한 시도가
  `SocketException: Network is unreachable`로 실패했다 — 소켓이 이미 열렸다는
  뜻이다. SSRF에서는 **연결 자체가 공격**이므로, 그 수정은 리뷰에서 옳아 보였을
  것이고 틀렸을 것이다.
- 제대로 닫으려면 `jdkhttp`가 받은 처리가 필요하다: OkHttp 자신의 리다이렉트
  추적을 끄고 루프를 직접 돌리며 홉마다 `RedirectGuard`로 재검증. 그건 어댑터의
  계약을 바꾸므로, 이미 파괴적 변경을 실은 릴리스에 얹지 말고 자기 릴리스로 낸다.

교차 라이브러리 발견이 사는 곳인 자매의
[정합성 감사](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.ko.md)에
기록돼 있다.

## 출시 완료

| 버전 | 내용 |
| --- | --- |
| 3.3.0 | 코어 `RedirectGuard`로 리다이렉트 홉 정합성. 툴 입력 스캐너가 비-`http(s)` 스킴과 프로토콜 상대 URL을 정책 전에 버리지 않게 됨. 파괴적 변경 1건 — `SsrfGuardedHttpClient`가 `Redirect.NEVER` delegate 요구 |
| 3.2.0 | LLM 툴 입력 가드의 `scanEmbedded`. `java.net.URI`가 파싱 못 하는 툴 입력 URL이 검증을 건너뛰지 않게 됨 |
| 3.1.1 | **보안:** LLM 툴 입력 가드의 대문자 스킴 우회 |
| 3.1.0 | LLM 코어 추출, LangChain4j 어댑터, WebClient DNS 갭, GraalVM 힌트 |

전체 노트는 [변경 이력](changelog.ko.md)에.

## 상시 관행

기능이 아니라, 계속 일어나야 하는 것들:

- **여기서 코어 로직이 바뀔 때마다
  [정합성 체크리스트](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.ko.md)를
  훑을 것** — 스캐너 수집, IP 분류, 리다이렉트 시맨틱, 차단 사유. 1회차가 양쪽
  모두에서 어긋남을 찾았고, 그중 둘은 이쪽에서 URL이 검증을 통째로 빠져나가게
  두고 있었다. **소스가 아니라 동작을 대조할 것**: 두 구현을 나란히 읽는 건
  애초에 3.1.1 우회가 양쪽에서 동시에 리뷰를 통과한 방식이다.
- **[릴리스 런북](releasing.ko.md)을 따를 것** — 특히, 어떤 버전이 미출시라고
  가정하기 전에 태그와 Maven Central을 확인할 것.

## 하지 않기로 한 것

- **JS 자매의 기능 집합 따라가기.** `maxBytes`·`checkUrl`·정책 헬퍼는 아직 JS
  전용이다. JS 전용 기능이 있는 건 정합성 실패가 아니다. **양쪽에 다 있는데
  다르게 구현된 것**이 실패다.
- **Spring Boot 4 라인** — 수요가 생기기 전까지. org 버전 정책상 그건 이름
  변경이 아니라 `4.x.y` 라인이다.
