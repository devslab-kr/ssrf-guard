package kr.devslab.ssrfguard.resttemplate;

import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.restclient.SsrfGuardClientHttpRequestInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import kr.devslab.ssrfguard.httpclient5.SsrfGuardHttpClient5AutoConfiguration;
import kr.devslab.ssrfguard.restclient.SsrfGuardRestClientAutoConfiguration;

/**
 * Auto-configuration that wires the SSRF defenses into Spring Boot's
 * auto-built {@code RestTemplateBuilder}. The same
 * {@link SsrfGuardClientHttpRequestInterceptor} the RestClient module uses
 * is reused here — the underlying {@code ClientHttpRequestInterceptor}
 * contract is identical.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link RestTemplate} is on the classpath.</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 * </ul>
 *
 * <p>The customizer's effect: every {@code RestTemplate} the consumer builds
 * via {@code RestTemplateBuilder} inherits the SSRF policy, including the
 * underlying {@code HttpComponentsClientHttpRequestFactory} pinned to the
 * SSRF-guarded {@link CloseableHttpClient}.
 */
@AutoConfiguration(after = {
        SsrfGuardHttpClient5AutoConfiguration.class,
        SsrfGuardRestClientAutoConfiguration.class
})
@AutoConfigureAfter({
        SsrfGuardHttpClient5AutoConfiguration.class,
        SsrfGuardRestClientAutoConfiguration.class
})
@ConditionalOnClass(RestTemplate.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardRestTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ssrfRestTemplateCustomizer")
    RestTemplateCustomizer ssrfRestTemplateCustomizer(
            HttpComponentsClientHttpRequestFactory factory,
            SsrfGuardClientHttpRequestInterceptor interceptor,
            UrlPolicy policy
    ) {
        return template -> {
            // Pin the request factory + interceptor onto the underlying
            // RestTemplate. The factory carries the SSRF-guarded HttpClient
            // (with the safe DNS resolver and redirect strategy); the
            // interceptor handles the pre-DNS scheme/host/port/IP-literal
            // checks.
            template.setRequestFactory(factory);

            // Prepend rather than .setInterceptors — consumers may already
            // have auth-header interceptors registered.
            var existing = new java.util.ArrayList<>(template.getInterceptors());
            existing.add(0, interceptor);
            template.setInterceptors(existing);
        };
    }
}
