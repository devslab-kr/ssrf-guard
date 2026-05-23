# 기여

ssrf-guard에 기여를 고려해주셔서 감사합니다.

## 개발 셋업

```bash
git clone https://github.com/devslab-kr/ssrf-guard
cd ssrf-guard
./gradlew build
```

Java 21+ 필요. 나머지는 Gradle wrapper가 처리.

## 테스트

```bash
./gradlew test
```

테스트 구성: 단위 테스트 (`NetUtil`, `SsrfGuardInterceptor`, `SafeDnsResolver`) + Spring 컨텍스트 통합 테스트 (`SsrfGuardAutoConfigurationTest`, `SsrfGuardIntegrationTest`). 통합 테스트는 Spring Boot 컨텍스트를 부팅하고 `MockWebServer`를 통해 실제 HTTP 호출을 흘려서 4-레이어 방어를 end-to-end로 exercise.

커버리지 리포트:

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## 코드 스타일

- Java 21, Spring Boot 3.5+ 관용구.
- Lombok 사용 (`@Data`, `@RequiredArgsConstructor`); 새 코드도 같은 스타일.
- `-Xlint:all` + `-Werror` 활성. 경고는 fix해야지 suppress하지 말 것 — suppress가 필요하면 명확한 이유와 코멘트.
- `-parameters` compile 플래그 on (AOP-readable parameter names). 끄지 말 것.

## PR 흐름

1. Repo fork + feature 브랜치 생성 (`git checkout -b feat/your-feature`).
2. 변경 작업. 테스트 추가/갱신 — 코드 변경 PR은 변경 없이는 fail하는 테스트를 최소 하나 추가해야 함.
3. [CHANGELOG.md](CHANGELOG.md)의 `[Unreleased]`에 한 줄 요약 추가.
4. 로컬에서 `./gradlew build` 실행 — CI 워크플로가 같은 task 실행하니 로컬 그린이면 보통 CI도 그린.
5. `main`을 향한 PR 생성. CI 배지가 그린이어야 머지 가능.

## 이슈 신고

[GitHub issue](https://github.com/devslab-kr/ssrf-guard/issues) 열기. 보안 관련 신고 (우회 또는 커버 못 하는 우회 클래스)는 <support@devslab.kr>로 직접 이메일 — 그 외는 public issue OK.

## 라이선스

기여 시 Apache License 2.0 ([LICENSE](LICENSE))로 라이선싱됩니다.
