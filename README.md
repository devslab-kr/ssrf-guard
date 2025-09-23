# SSRF Guard Spring Boot Starter

SSRF(Server-Side Request Forgery) 공격을 방지하는 Spring Boot Starter입니다. 

## 주요 기능

- **화이트리스트 기반 호스트 제어**: 허용된 호스트만 접근 가능
- **사설망 IP 차단**: 내부 네트워크 접근 방지
- **스킴/포트 제한**: HTTP/HTTPS 및 특정 포트만 허용
- **리다이렉트 검증**: 리다이렉트 시에도 동일한 보안 정책 적용
- **TOCTOU 공격 완화**: DNS 검증과 실제 연결이 일치하도록 보장

## 설치

### 의존성 추가

```xml
<dependency>
    <groupId>com.devs.lab</groupId>
    <artifactId>ssrf-guard-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Spring Boot가 자동으로 설정을 적용합니다.

## 설정

`application.yml`에 다음과 같이 설정하세요:

```yaml
ssrf:
  guard:
    enabled: true
    allowed-schemes: [ "http", "https" ]
    allowed-ports: [ -1, 80, 443 ]   # -1=기본 포트 허용
    block-private-networks: true
    follow-redirects: true

    # 화이트리스트 (정확 일치)
    exact-hosts:
      - api.devslab.kr
      - eds8282.com

    # 화이트리스트 (도메인 서픽스; 전체 서브도메인 포함)
    suffixes:
      - api.devslab.kr
      - eds8282.com

    connect-timeout: 5s
    read-timeout: 10s
```

### 설정 옵션

| 속성 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | SSRF Guard 활성화 여부 |
| `allowed-schemes` | `["http", "https"]` | 허용할 스킴 목록 |
| `allowed-ports` | `[-1, 80, 443]` | 허용할 포트 목록 (-1은 기본 포트) |
| `block-private-networks` | `true` | 사설망/루프백 IP 차단 여부 |
| `follow-redirects` | `true` | 리다이렉트 허용 여부 |
| `exact-hosts` | `[]` | 정확히 일치하는 호스트 화이트리스트 |
| `suffixes` | `[]` | 도메인 서픽스 화이트리스트 (서브도메인 포함) |
| `connect-timeout` | `5s` | 연결 타임아웃 |
| `read-timeout` | `10s` | 읽기 타임아웃 |

## 사용법

### 기본 사용

설정만 추가하면 모든 `RestClient`에 자동으로 적용됩니다:

```java
@Service
public class ExternalApiService {
    
    private final RestClient restClient;
    
    public ExternalApiService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.devslab.kr")
            .build();
    }
    
    public String callExternalApi() {
        return restClient.get()
            .uri("/data")
            .retrieve()
            .body(String.class);
    }
}
```

### 특정 RestClient만 별도 정책

```java
@Configuration
public class RestClientConfig {
    
    @Bean
    public RestClient externalFooClient(RestClient.Builder builder) {
        return builder
            .baseUrl("https://foo.example.org")
            // 필요 시 추가 인터셉터를 더 붙이거나, 다른 factory로 교체 가능
            .build();
    }
}
```

### 커스텀 HttpClient 사용

다른 구현을 사용하고 싶으면 동일 타입 빈을 직접 정의하세요:

```java
@Configuration
public class CustomHttpClientConfig {
    
    @Bean
    public CloseableHttpClient ssrfHttpClient(SafeDnsResolver dns) {
        return HttpClients.custom()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dns)
                        .build()
                )
                .setProxy(new HttpHost("corp-proxy", 3128))
                .build();
    }
}
```

## 확장 팁

### 1. 커스텀 DNS Resolver

```java
@Bean
public SafeDnsResolver customDnsResolver(SsrfGuardProperties props) {
    return new SafeDnsResolver(
        props.getExactHosts(),
        props.getSuffixes(),
        props.isBlockPrivateNetworks()
    ) {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            // 커스텀 로직 추가
            logger.info("Resolving host: {}", host);
            return super.resolve(host);
        }
    };
}
```

### 2. 추가 인터셉터

```java
@Bean
public RestClientCustomizer additionalInterceptor() {
    return builder -> builder
        .requestInterceptor((request, body, execution) -> {
            // 추가 검증 로직
            return execution.execute(request, body);
        });
}
```

### 3. 조건부 활성화

```java
@ConditionalOnProperty(name = "app.external-api.enabled", havingValue = "true")
@Bean
public RestClient conditionalRestClient(RestClient.Builder builder) {
    return builder.build();
}
```

## 보안 고려사항

1. **화이트리스트 관리**: 필요한 호스트만 최소한으로 등록
2. **정기적 검토**: 등록된 호스트의 유효성을 정기적으로 검토
3. **로깅**: 차단된 요청에 대한 로깅 및 모니터링 구성
4. **테스트**: 보안 정책이 올바르게 작동하는지 테스트

## 버전 관리

이 프로젝트는 **Semantic Release**를 사용하여 완전 자동화된 버전 관리를 제공합니다.

### 🚀 자동 릴리스 (추천)

커밋 메시지만으로 자동 버전 업데이트 및 GitHub Release 생성:

```bash
# 패치 버전 (1.0.0 → 1.0.1)
git commit -m "fix: 버그 수정"

# 마이너 버전 (1.0.0 → 1.1.0)
git commit -m "feat: 새로운 기능 추가"

# 메이저 버전 (1.0.0 → 2.0.0)
git commit -m "feat!: 호환성 깨지는 변경"

# main 브랜치에 푸시하면 자동으로:
# 1. 버전 계산 (1.0.0 → 1.1.0)
# 2. pom.xml 버전 업데이트
# 3. Git 태그 생성 (v1.1.0)
# 4. JAR 빌드 및 GitHub Release 생성
# 5. JAR 파일 업로드
git push origin main
```

### 📋 커밋 메시지 규칙

- `fix:` → 패치 버전 (버그 수정)
- `feat:` → 마이너 버전 (새 기능)
- `feat!:` 또는 `BREAKING CHANGE:` → 메이저 버전
- `docs:`, `style:`, `refactor:`, `test:`, `chore:` → 버전 변경 없음

### 🔧 수동 릴리스 (필요시)

```bash
# 수동 태그 생성
git tag v1.0.0
git push origin v1.0.0
```