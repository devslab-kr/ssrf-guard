package kr.devslab.ssrfguard.feign;

import feign.Feign;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that wires the SSRF defenses into Spring Cloud
 * OpenFeign.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link Feign} is on the classpath.</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 * </ul>
 *
 * <p>The interceptor bean is picked up automatically by Spring Cloud
 * OpenFeign — any {@code feign.RequestInterceptor} bean in the application
 * context is applied to every {@code @FeignClient}-generated proxy.
 *
 * <p>Metrics: Micrometer-backed when {@code micrometer-core} is on the
 * classpath ({@link MetricsConfiguration}); no-op otherwise. The outer
 * class never references Micrometer types, so consumers without
 * Micrometer boot cleanly.
 */
@AutoConfiguration
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsrfGuardMetrics.class)
    SsrfGuardMetrics ssrfGuardMetricsFeign() {
        return NoOpSsrfGuardMetrics.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    UrlPolicy ssrfUrlPolicyFeign(SsrfGuardProperties props,
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
    SsrfGuardFeignRequestInterceptor ssrfGuardFeignRequestInterceptor(UrlPolicy policy) {
        return new SsrfGuardFeignRequestInterceptor(policy);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(SsrfGuardMetrics.class)
        SsrfGuardMetrics micrometerSsrfGuardMetricsFeign(
                ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
            var reg = meterRegistry.getIfAvailable();
            return reg == null
                    ? NoOpSsrfGuardMetrics.INSTANCE
                    : new kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics(reg);
        }
    }
}
