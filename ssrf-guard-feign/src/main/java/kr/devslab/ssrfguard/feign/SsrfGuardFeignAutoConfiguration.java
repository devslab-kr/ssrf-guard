package kr.devslab.ssrfguard.feign;

import feign.Feign;
import io.micrometer.core.instrument.MeterRegistry;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics;
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
 */
@AutoConfiguration
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardMetrics ssrfGuardMetricsFeign(ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry reg = meterRegistry.getIfAvailable();
        return reg == null ? NoOpSsrfGuardMetrics.INSTANCE : new MicrometerSsrfGuardMetrics(reg);
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
}
