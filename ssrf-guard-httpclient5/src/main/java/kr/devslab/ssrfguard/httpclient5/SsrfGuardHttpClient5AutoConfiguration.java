package kr.devslab.ssrfguard.httpclient5;

import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the Apache HttpClient 5 SSRF defenses (DNS resolver + redirect
 * strategy) as Spring beans. Higher-level modules ({@code ssrf-guard-restclient},
 * future {@code ssrf-guard-resttemplate-httpclient}) depend on the
 * {@link CloseableHttpClient} bean here.
 *
 * <p>Gated on:
 * <ul>
 *   <li>{@code CloseableHttpClient.class} on the classpath — the entire
 *       module is opt-in.</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(CloseableHttpClient.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardHttpClient5AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    HostPolicy ssrfHostPolicy(SsrfGuardProperties props) {
        return new HostPolicy(props.getExactHosts(), props.getSuffixes());
    }

    @Bean
    @ConditionalOnMissingBean
    SafeDnsResolver ssrfSafeDnsResolver(HostPolicy hostPolicy,
                                        SsrfGuardProperties props,
                                        ObjectProvider<SsrfGuardMetrics> metrics) {
        return new SafeDnsResolver(hostPolicy, props.isBlockPrivateNetworks(),
                metrics.getIfAvailable(() -> NoOpSsrfGuardMetrics.INSTANCE));
    }

    @Bean
    @ConditionalOnMissingBean
    CloseableHttpClient ssrfHttpClient(SafeDnsResolver dns,
                                       SsrfGuardProperties props,
                                       ObjectProvider<SsrfGuardMetrics> metrics) {
        var builder = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dns)
                        .build())
                .disableAutomaticRetries();

        if (props.isFollowRedirects()) {
            builder.setRedirectStrategy(new SafeRedirectStrategy(
                    dns,
                    props.getAllowedSchemes(),
                    metrics.getIfAvailable(() -> NoOpSsrfGuardMetrics.INSTANCE)
            ));
        } else {
            builder.disableRedirectHandling();
        }

        return builder.build();
    }
}
