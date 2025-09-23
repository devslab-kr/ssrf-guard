package devs.lab.ssrf.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "ssrf.guard")
public class SsrfGuardProperties {

    /**
     * 전체 기능 on/off
     */
    private boolean enabled = true;

    /**
     * 허용 스킴 (기본 http/https)
     */
    private Set<String> allowedSchemes = Set.of("http", "https");

    /**
     * 허용 포트 (기본 제한: -1=기본포트, 80, 443)
     */
    private Set<Integer> allowedPorts = Set.of(-1, 80, 443);

    /**
     * 화이트리스트 – 정확히 일치하는 호스트
     * <p>예: ["api.trusted-service.com"]
     */
    private List<String> exactHosts = List.of();

    /**
     * 화이트리스트 – 서픽스(도메인) 허용
     * <p>예: ["partner.example.com", "example.org"]
     * <p>"sub.partner.example.com" 도 허용
     */
    private List<String> suffixes = List.of();

    /**
     * 사설망/루프백/링크로컬/멀티캐스트 IP 차단 여부
     */
    private boolean blockPrivateNetworks = true;

    /**
     * DNS 결과 캐시 사용(선택). HttpClient 내부 캐시는 별도이지만,
     * 우리 Resolver는 매 요청 resolve 호출을 하므로 외부 DNS 캐시가 일반적으로 동작.
     * <p>필요 시 외부 레벨에서 캐시(예: JVM DNS Cache, netty-resolver-dns) 고려.
     */
    private boolean enableAdditionalDnsCaching = false;

    /**
     * 연결 타임아웃
     */
    private Duration connectTimeout = Duration.ofSeconds(5);
    
    /**
     * 읽기 타임아웃
     */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * 리다이렉트 허용 여부 (허용 시 hop마다 검증)
     */
    private boolean followRedirects = true;
}