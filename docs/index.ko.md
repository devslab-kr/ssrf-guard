# ssrf-guard

> Spring Boot용 SSRF(Server-Side Request Forgery) 방어 — 화이트리스트 기반 outbound HTTP 가드. 사설망 차단, 리다이렉트 재검증, TOCTOU 완화.

## 왜 필요한가

사용자가 URL을 직접 넣을 수 있는 Spring Boot 서비스 — webhook 콜백, 이미지 가져오기 helper, "URL에서 import" 같은 모든 흐름 — 는 `http://169.254.169.254/latest/meta-data/` 한 번이면 AWS 크리덴셜이 새거나, 내부 서브넷이 스캔되거나, loopback에만 노출된 admin 엔드포인트가 호출됩니다. SSRF가 [OWASP Top 10 2021의 #7](https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/)이 된 이유가 정확히 이 "기본적으로 자기 서버를 신뢰" 결함입니다.

ssrf-guard는 모든 outbound 호출에 4단계 필터를 두르고, plain Spring Boot 3.5+에서 동작하며, classpath에 올리고 `application.yml`에 화이트리스트만 적으면 끝입니다.

## 4단계 방어

1. **프론트라인 인터셉터** — 모든 URL에 대해 DNS 전에 스킴 / 호스트 / 포트 문자열 체크
2. **DNS 시점 화이트리스트 재검증** — 호스트 정책을 한 번 더, 위조 URL이 빠져나갈 여지 차단
3. **사설망 IP 필터** — loopback, RFC-1918, link-local (`169.254.169.254` AWS 메타데이터 포함), CGNAT, IPv6 ULA 모두 차단
4. **리다이렉트 재검증** — 3xx 홉마다 동일한 스킴/호스트/IP 룰 적용. 공격자가 `example.com` 화이트리스트 후 `169.254.169.254`로 302 redirect 못 시킴

DNS resolver가 검증한 `InetAddress` 배열이 Apache HttpClient의 `Socket.connect()`에 전달되는 그 배열 — 검증과 연결 사이에 DNS 재조회가 일어나지 않으니 TOCTOU 윈도우가 닫힙니다.

## 빠른 설치

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

```yaml
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - example.org
```

Spring Boot가 나머지를 자동 구성합니다. 전체 레퍼런스는 [설치 가이드](getting-started/installation.md), 5분 워크스루는 [빠른 시작](getting-started/quickstart.md).

## 어디서

- **GitHub**: <https://github.com/devslab-kr/ssrf-guard>
- **Maven Central**: <https://central.sonatype.com/artifact/kr.devslab/ssrf-guard>
- **Docs**: <https://ssrf-guard.devslab.kr/>

## 라이선스

Apache License 2.0. [DevsLab 오픈소스 툴킷](https://github.com/devslab-kr) 일부.
