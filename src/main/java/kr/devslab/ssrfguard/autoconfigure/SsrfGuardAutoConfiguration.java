package kr.devslab.ssrfguard.autoconfigure;

import kr.devslab.ssrfguard.security.SafeDnsResolver;
import kr.devslab.ssrfguard.security.SafeRedirectStrategy;
import kr.devslab.ssrfguard.security.SsrfGuardInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration that wires the SSRF defenses into Spring Boot's
 * auto-built {@code RestClient.Builder}.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link RestClient} is on the classpath (i.e. the consumer has
 *       {@code spring-web} — gates against pure-WebFlux consumers).</li>
 *   <li>{@code ssrf.guard.enabled} is {@code true} (the default, but consumers
 *       can disable the guard entirely with one property).</li>
 * </ul>
 *
 * <p>Once active it registers, in order:
 * <ol>
 *   <li>{@link SafeDnsResolver} — whitelist + private-IP filter at DNS time.</li>
 *   <li>{@link CloseableHttpClient} built off Apache HttpClient 5 with the
 *       resolver wired in and ({@code if followRedirects=true}) a
 *       {@link SafeRedirectStrategy} re-validating every hop.</li>
 *   <li>{@link HttpComponentsClientHttpRequestFactory} with the configured
 *       connect / read timeouts.</li>
 *   <li>{@link SsrfGuardInterceptor} — the front-line scheme/host/port check
 *       before any DNS lookup happens.</li>
 *   <li>A {@link RestClientCustomizer} that pins the request factory and
 *       interceptor onto Spring Boot's auto-configured {@code RestClient.Builder},
 *       so every {@code RestClient} the consumer builds picks up the same
 *       policy automatically.</li>
 * </ol>
 *
 * <p>Each bean is {@link ConditionalOnMissingBean} so consumers can replace
 * any piece (e.g. provide their own {@code CloseableHttpClient} with auth
 * headers and still inherit the resolver / interceptor).
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SafeDnsResolver ssrfSafeDnsResolver(SsrfGuardProperties props) {
        return new SafeDnsResolver(props.getExactHosts(), props.getSuffixes(), props.isBlockPrivateNetworks());
    }

    @Bean
    @ConditionalOnMissingBean
    CloseableHttpClient ssrfHttpClient(SafeDnsResolver dns, SsrfGuardProperties props) {
        var builder = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dns)
                        .build())
                .disableAutomaticRetries();

        if (props.isFollowRedirects()) {
            builder.setRedirectStrategy(new SafeRedirectStrategy(dns, props.getAllowedSchemes()));
        } else {
            builder.disableRedirectHandling();
        }

        return builder.build();
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

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardInterceptor ssrfGuardInterceptor(SsrfGuardProperties props) {
        return new SsrfGuardInterceptor(
                props.getExactHosts(),
                props.getSuffixes(),
                props.getAllowedSchemes(),
                props.getAllowedPorts()
        );
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
            SsrfGuardInterceptor interceptor
    ) {
        return builder -> builder
                .requestFactory(factory)
                .requestInterceptor(interceptor);
    }
}
