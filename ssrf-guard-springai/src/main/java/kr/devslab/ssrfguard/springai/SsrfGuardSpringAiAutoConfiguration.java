package kr.devslab.ssrfguard.springai;

import io.micrometer.core.instrument.MeterRegistry;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that wires the SSRF defenses into Spring AI tool calls.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link ToolCallback} is on the classpath (i.e. consumer pulls in
 *       Spring AI 1.0+).</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 *   <li>{@code ssrf.guard.springai.wrap-tool-callbacks=true} (default true) —
 *       set false to opt out of automatic bean-post-processor wrapping
 *       while still allowing manual use of {@link SsrfGuardedToolCallbacks}.</li>
 * </ul>
 *
 * <p>The {@link BeanPostProcessor} below wraps every {@code ToolCallback}
 * bean Spring registers, so a consumer's hand-rolled {@code @Bean ToolCallback}
 * gets the URL policy applied without any wiring code. Manual wraps via
 * {@link SsrfGuardedToolCallbacks#wrap} are idempotent so users can do both.
 */
@AutoConfiguration
@ConditionalOnClass(ToolCallback.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardSpringAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SsrfGuardMetrics ssrfGuardMetricsSpringAi(ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry reg = meterRegistry.getIfAvailable();
        return reg == null ? NoOpSsrfGuardMetrics.INSTANCE : new MicrometerSsrfGuardMetrics(reg);
    }

    @Bean
    @ConditionalOnMissingBean
    UrlPolicy ssrfUrlPolicySpringAi(SsrfGuardProperties props,
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

    /**
     * BeanPostProcessor that wraps every {@link ToolCallback} bean with
     * {@link SsrfGuardedToolCallback}. Idempotent — already-wrapped callbacks
     * pass through.
     */
    @Bean
    @ConditionalOnProperty(prefix = "ssrf.guard.springai", name = "wrap-tool-callbacks",
            havingValue = "true", matchIfMissing = true)
    BeanPostProcessor ssrfGuardToolCallbackBeanPostProcessor(UrlPolicy policy) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ToolCallback cb && !(bean instanceof SsrfGuardedToolCallback)) {
                    return new SsrfGuardedToolCallback(cb, policy);
                }
                return bean;
            }
        };
    }
}
