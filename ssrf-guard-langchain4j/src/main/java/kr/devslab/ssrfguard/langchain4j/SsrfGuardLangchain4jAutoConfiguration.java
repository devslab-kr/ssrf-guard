package kr.devslab.ssrfguard.langchain4j;

import dev.langchain4j.service.tool.ToolExecutor;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that wires the SSRF defenses into LangChain4j tool
 * execution.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@link ToolExecutor} is on the classpath (i.e. consumer pulls in
 *       LangChain4j 1.x).</li>
 *   <li>{@code ssrf.guard.enabled=true} (default).</li>
 *   <li>{@code ssrf.guard.langchain4j.wrap-tool-executors=true} (default
 *       true) — set false to opt out of automatic wrapping while still
 *       allowing manual use of {@link SsrfGuardedToolExecutors}.</li>
 * </ul>
 *
 * <p>The {@link BeanPostProcessor} below wraps every {@link ToolExecutor}
 * bean Spring registers. A consumer's hand-rolled {@code @Bean ToolExecutor}
 * (or a {@code @Tool}-annotated class registered as a bean) gets the URL
 * policy applied without any wiring code.
 *
 * <p>Mirrors {@code SsrfGuardSpringAiAutoConfiguration} — same model,
 * different framework. Both share the same {@link UrlPolicy} bean shape
 * so a Spring AI app and a LangChain4j app configured against the same
 * `ssrf.guard.*` properties block identical attacks.
 */
@AutoConfiguration
@ConditionalOnClass(ToolExecutor.class)
@EnableConfigurationProperties(SsrfGuardProperties.class)
@ConditionalOnProperty(prefix = "ssrf.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SsrfGuardLangchain4jAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsrfGuardMetrics.class)
    SsrfGuardMetrics ssrfGuardMetricsLangchain4j() {
        return NoOpSsrfGuardMetrics.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    UrlPolicy ssrfUrlPolicyLangchain4j(SsrfGuardProperties props,
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
     * Wraps every {@link ToolExecutor} bean with {@link SsrfGuardedToolExecutor}.
     * Idempotent — already-wrapped executors pass through.
     */
    @Bean
    @ConditionalOnProperty(prefix = "ssrf.guard.langchain4j", name = "wrap-tool-executors",
            havingValue = "true", matchIfMissing = true)
    BeanPostProcessor ssrfGuardToolExecutorBeanPostProcessor(UrlPolicy policy) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ToolExecutor exec && !(bean instanceof SsrfGuardedToolExecutor)) {
                    return new SsrfGuardedToolExecutor(exec, policy);
                }
                return bean;
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(SsrfGuardMetrics.class)
        SsrfGuardMetrics micrometerSsrfGuardMetricsLangchain4j(
                ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
            var reg = meterRegistry.getIfAvailable();
            return reg == null
                    ? NoOpSsrfGuardMetrics.INSTANCE
                    : new kr.devslab.ssrfguard.core.MicrometerSsrfGuardMetrics(reg);
        }
    }
}
