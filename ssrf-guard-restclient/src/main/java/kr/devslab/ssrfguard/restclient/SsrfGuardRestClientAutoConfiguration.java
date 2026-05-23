package kr.devslab.ssrfguard.restclient;

import io.micrometer.core.instrument.MeterRegistry;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import kr.devslab.ssrfguard.httpclient5.SsrfGuardHttpClient5AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;

/**
 * Auto-configuration that wires the SSRF defenses into Spring Boot's
 * auto-built {@code RestClient.Builder}.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link RestClient} is on the classpath (i.e. the consumer has
 *       {@code spring-web}).</li>
 *   <li>{@code ssrf.guard.enabled} is {@code true} (the default).</li>
 * </ul>
 *
 * <p>Registers, in order:
 * <ol>
 *   <li>{@link SsrfGuardMetrics} — Micrometer-backed if a {@code MeterRegistry}
 *       bean is present, otherwise no-op.</li>
 *   <li>{@link UrlPolicy} — pre-DNS scheme/host/port/IP-literal/userinfo gate.</li>
 *   <li>{@link SsrfGuardClientHttpRequestInterceptor} — wires the policy onto
 *       every {@code RestClient} the consumer builds.</li>
 *   <li>{@link HttpComponentsClientHttpRequestFactory} with the configured
 *       timeouts — uses the {@link CloseableHttpClient} that the
 *       {@link SsrfGuardHttpClient5AutoConfiguration} provides (DNS resolver +
 *       redirect strategy already wired).</li>
 *   <li>A {@link RestClientCustomizer} that pins the request factory and
 *       interceptor onto Spring Boot's auto-configured
 *       {@code RestClient.Builder}.</li>
 * </ol>
 */
@AutoConfiguration(after = SsrfGuardHttpClient5AutoConfiguration.class)
@AutoConfigureAfter(SsrfGuardHttpClient5AutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardRestClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardMetrics ssrfGuardMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry reg = meterRegistry.getIfAvailable();
        return reg == null ? NoOpSsrfGuardMetrics.INSTANCE : new MicrometerSsrfGuardMetrics(reg);
    }

    @Bean
    @ConditionalOnMissingBean
    UrlPolicy ssrfUrlPolicy(SsrfGuardProperties props,
                            ObjectProvider<HostPolicy> hostPolicy,
                            SsrfGuardMetrics metrics) {
        HostPolicy hp = hostPolicy.getIfAvailable(
                () -> new HostPolicy(props.getExactHosts(), props.getSuffixes()));
        return new UrlPolicy(
                props.getAllowedSchemes(),
                props.getAllowedPorts(),
                hp,
                props.isRejectIpLiteralHosts(),
                props.isRejectUserInfo(),
                metrics
        );
    }

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardClientHttpRequestInterceptor ssrfGuardClientHttpRequestInterceptor(UrlPolicy policy) {
        return new SsrfGuardClientHttpRequestInterceptor(policy);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ssrfHttpRequestFactory")
    HttpComponentsClientHttpRequestFactory ssrfHttpRequestFactory(CloseableHttpClient httpClient,
                                                                  SsrfGuardProperties props) {
        var f = new HttpComponentsClientHttpRequestFactory(httpClient);
        f.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        f.setReadTimeout((int) props.getReadTimeout().toMillis());
        return f;
    }

    /**
     * Customises Spring Boot's auto-configured {@code RestClient.Builder} so
     * every {@code RestClient} the consumer constructs gets the SSRF policy
     * applied — no need to remember to wire it onto each one.
     */
    @Bean
    @ConditionalOnMissingBean(name = "ssrfRestClientCustomizer")
    RestClientCustomizer ssrfRestClientCustomizer(
            HttpComponentsClientHttpRequestFactory factory,
            SsrfGuardClientHttpRequestInterceptor interceptor
    ) {
        return builder -> builder
                .requestFactory(factory)
                .requestInterceptor(interceptor);
    }
}
