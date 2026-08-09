# 릴리스

[English](releasing.md)

`kr.devslab:ssrf-guard`의 한 버전이 Maven Central에 도달하는 과정.
메인테이너용입니다.

## 먼저: 그 버전이 이미 나갔는지 확인한다

`gradle.properties`가 담고 있는 건 **다음 개발 버전**이지, 아직 안 나간 다음
버전이 아닙니다:

```properties
VERSION=3.4.0-SNAPSHOT
```

`3.4.0-SNAPSHOT`은 *3.3.0이 출시됐고 3.4.0을 만드는 중*이라는 뜻입니다. 3.4.0이
미출시로 대기 중이라는 뜻이 **아닙니다.** 반대로 읽기 쉽고 대가가 큽니다 —
2026-08-09에 실제로 그렇게 읽었고, 보안 수정 2건이 **이미 Maven Central에 있는
버전 번호** 아래로 `main`에 올라갔습니다. 태그를 옮기지 않았기 때문에만 잘못
나간 게 없었습니다.

그러니 손대기 전에 파일이 아니라 **레지스트리에** 물어보세요:

```bash
git ls-remote --tags origin 'refs/tags/v*' | tail -5
curl -s https://repo1.maven.org/maven2/kr/devslab/ssrf-guard/maven-metadata.xml \
  | grep -oE '<version>[^<]+</version>' | tail -5
```

**출시된 버전은 불변입니다.** 이미 출시된 번호 아래로 작업이 들어갔다면, 태그를
옮기지 말고 그 버전의 changelog 섹션도 고치지 마세요 — 아티팩트와 노트는 계속
일치해야 합니다. 출시본 섹션을 태그에서 복원하고 **새 버전을 여세요**:

```bash
git show v3.3.0:CHANGELOG.md | sed -n '/^## \[3.3.0\]/,/^## \[/p'
```

## 릴리스 절차

1. `gradle.properties`의 `VERSION`에서 `-SNAPSHOT` 제거
2. `CHANGELOG.md`에 섹션이 있는지, 그리고 **제목이 릴리스를 설명하는지**
   확인 — 처음 머지된 PR이 뭐였는지가 아니라. 자기 보안 내용을 축소해 말하는
   릴리스 노트는 사람들이 건너뜁니다.
3. PR → CI 그린 → 머지
4. 태그를 밀면 되돌릴 수 없습니다:

```bash
git tag v3.4.0
git push origin v3.4.0
```

그러면 `release.yml`이 Maven Central에 배포하고 changelog 섹션으로 GitHub
Release를 만듭니다.

## 태그 푸시 전에

로컬에서 `./gradlew build` 그린 — 한 모듈이 아니라 **전체 스위트**. 그다음 아래
표면을 전부 훑고 **갱신됐거나 명시적으로 N/A**임을 확인하세요. 버전만 오른 jar에
낡은 공개 문서가 딸려가는 게 흔한 실수입니다. 독자는 문서 사이트나 org 프로필에
도착해서 이전 릴리스의 이야기를 봅니다.

| 표면 | 보통 |
| --- | --- |
| [`devslab-kr/.github`](https://github.com/devslab-kr/.github) 프로필 README (양어) | N/A — Maven Central 배지, 하드코딩 버전 없음 |
| 이 repo의 `README.md` + `README.ko.md` | 설치 좌표 |
| `CHANGELOG.md`, `docs/changelog.md`, `docs/changelog.ko.md` | 셋 다, 같은 내용 |
| `docs/` — `index`, `getting-started/installation`, 가이드, 양어 | `grep -rn "<이전 버전>" docs/` |
| GitHub Discussions | 이 repo는 비활성이라 N/A |
| [`devslab-kr/devslab-examples`](https://github.com/devslab-kr/devslab-examples) | `ssrf-guard-*` 데모마다 버전 고정 — 그리고 **동작** 변경이면 범프만으로 부족할 수 있음 |

마지막 칸이 중요합니다. 3.3.0은 `SsrfGuardedHttpClient`가 스스로 리다이렉트를
따라가는 delegate를 거부하게 만들었습니다. jdkhttp 데모는 여전히 컴파일됐지만,
주석이 *"어떤 HttpClient든 감싸면 된다"* 였고 그건 거짓이 됐습니다. Central에
올라온 뒤 **실제 아티팩트로 데모를 빌드해 보세요.**

## 자매 라이브러리

[`@devslab/ssrf-guard-js`](https://github.com/devslab-kr/ssrf-guard-js)가 같은
보안 모델을 JS/TS로, **자기 버전 라인으로** 냅니다 — 모델을 공유하지 릴리스
라인을 공유하지 않습니다. 그쪽 릴리스 런북은
[docs/releasing.md](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/releasing.ko.md)이고,
이번 릴리스가 코어 로직(스캐너·IP 분류·리다이렉트 시맨틱·차단 사유)을 바꿨다면
출시 전에
[정합성 체크리스트](https://github.com/devslab-kr/ssrf-guard-js/blob/main/docs/parity.ko.md)를
훑으세요. 그 감사 1회차는 **양쪽 모두**에서 어긋남을 찾아냈습니다.
