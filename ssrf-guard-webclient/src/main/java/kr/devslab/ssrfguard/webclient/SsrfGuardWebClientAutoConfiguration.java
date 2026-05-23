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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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
 * <p>Two defense layers wired here:
 * <ol>
 *   <li>{@link SsrfGuardExchangeFilterFunction} — URL-time check on every
 *       outbound request before reactor-netty even resolves the host.</li>
 *   <li>{@link SsrfGuardReactorAddressResolverGroup} — DNS-time check that
 *       filters resolved IPs against the private/loopback/metadata ranges.
 *       Closes the v3.0.0 gap where a whitelisted host could resolve to a
 *       private IP (DNS rebinding / metadata-server tricks) and the filter
 *       wouldn't catch it because by then the URL string had passed. v3.1+.
 *       The {@link ReactorNettyConnectorConfiguration} inner class is
 *       gated on reactor-netty's presence so non-Netty WebFlux backends
 *       (Jetty Reactive, Helidon) skip the connector wiring without
 *       breaking the URL-time defense.</li>
 * </ol>
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
    WebClientCustomizer ssrfWebClientCustomizer(
            SsrfGuardExchangeFilterFunction filter,
            ObjectProvider<ReactorClientHttpConnector> ssrfReactorConnector) {
        return builder -> {
            builder.filter(filter);
            // Attach our DNS-filtering connector iff the reactor-netty path
            // is active. Pulling the connector via ObjectProvider keeps this
            // tolerant of non-Netty backends (Jetty Reactive / Helidon).
            ReactorClientHttpConnector connector = ssrfReactorConnector.getIfAvailable();
            if (connector != null) {
                builder.clientConnector(connector);
            }
        };
    }

    /**
     * Builds the reactor-netty {@link HttpClient} with our DNS-filtering
     * resolver attached. Gated on {@link HttpClient} being on the classpath
     * so the autoconfig stays compatible with non-Netty WebFlux backends —
     * those will boot with just the URL-time filter, no connector swap.
     *
     * <p>Marked {@code @ConditionalOnMissingBean(ReactorClientHttpConnector.class)}
     * so consumers who have their own connector (custom pool, proxy,
     * mutual TLS) take precedence. Those consumers can still get the DNS
     * filter by calling {@code httpClient.resolver(...)} themselves with
     * a bean of {@link SsrfGuardReactorAddressResolverGroup}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HttpClient.class)
    static class ReactorNettyConnectorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SsrfGuardReactorAddressResolverGroup ssrfReactorAddressResolverGroup(
                SsrfGuardProperties props,
                SsrfGuardMetrics metrics) {
            return new SsrfGuardReactorAddressResolverGroup(props.isBlockPrivateNetworks(), metrics);
        }

        @Bean
        @ConditionalOnMissingBean
        ReactorClientHttpConnector ssrfReactorClientHttpConnector(
                SsrfGuardReactorAddressResolverGroup resolverGroup) {
            HttpClient httpClient = HttpClient.create().resolver(resolverGroup);
            return new ReactorClientHttpConnector(httpClient);
        }
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
