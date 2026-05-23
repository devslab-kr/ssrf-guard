package kr.devslab.ssrfguard.webclient;

import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration that wires the SSRF defenses into Spring Boot's
 * auto-built {@code WebClient.Builder}.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link WebClient} is on the classpath (i.e. consumer pulls in WebFlux).</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 * </ul>
 *
 * <p>Metrics: Micrometer-backed when {@code micrometer-core} is on the
 * classpath ({@link MetricsConfiguration} below); no-op otherwise. The
 * outer class never references Micrometer types, so a consumer without
 * Micrometer boots cleanly — see the v3.0.1 changelog entry.
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardWebClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsrfGuardMetrics.class)
    SsrfGuardMetrics ssrfGuardMetricsWebClient() {
        return NoOpSsrfGuardMetrics.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    UrlPolicy ssrfUrlPolicyWebClient(SsrfGuardProperties props,
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
    SsrfGuardExchangeFilterFunction ssrfGuardExchangeFilterFunction(UrlPolicy policy) {
        return new SsrfGuardExchangeFilterFunction(policy);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ssrfWebClientCustomizer")
    WebClientCustomizer ssrfWebClientCustomizer(SsrfGuardExchangeFilterFunction filter) {
        return builder -> builder.filter(filter);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(SsrfGuardMetrics.class)
        SsrfGuardMetrics micrometerSsrfGuardMetricsWebClient(
                ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
            var reg = meterRegistry.getIfAvailable();
            return reg == null
                    ? NoOpSsrfGuardMetrics.INSTANCE
                    : new kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics(reg);
        }
    }
}
