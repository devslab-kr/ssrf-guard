package kr.devslab.ssrfguard.autoconfigure;

import kr.devslab.ssrfguard.security.SafeDnsResolver;
import kr.devslab.ssrfguard.security.SsrfGuardInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots a full Spring context with the starter on the classpath and asserts
 * every public bean the auto-config promises is registered:
 * {@link SafeDnsResolver}, {@link CloseableHttpClient},
 * {@link HttpComponentsClientHttpRequestFactory}, {@link SsrfGuardInterceptor},
 * and the {@link RestClientCustomizer} that pins them onto Spring Boot's
 * auto-configured builder.
 */
@SpringBootTest
class SsrfGuardAutoConfigurationTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void safeDnsResolver_isRegistered() {
        assertThat(applicationContext.getBean(SafeDnsResolver.class)).isNotNull();
    }

    @Test
    void httpClient_isRegistered() {
        assertThat(applicationContext.getBean(CloseableHttpClient.class)).isNotNull();
    }

    @Test
    void requestFactory_isRegistered() {
        assertThat(applicationContext.getBean(HttpComponentsClientHttpRequestFactory.class)).isNotNull();
    }

    @Test
    void interceptor_isRegistered() {
        assertThat(applicationContext.getBean(SsrfGuardInterceptor.class)).isNotNull();
    }

    @Test
    void restClientCustomizer_isRegistered() {
        // Spring Boot registers its own RestClientCustomizer (for HTTP message
        // converters), so look ours up by name instead of by type.
        assertThat(applicationContext.getBean("ssrfRestClientCustomizer", RestClientCustomizer.class))
                .isNotNull();
    }

    @Test
    void autoconfigBean_isLoaded() {
        // The auto-configuration class itself is a bean — confirms it ran.
        assertThat(applicationContext.containsBean(
                "kr.devslab.ssrfguard.autoconfigure.SsrfGuardAutoConfiguration")).isTrue();
    }

    /**
     * Sibling test: disabling the property MUST keep all SSRF beans out of the
     * context so a consumer can opt out completely. Spring Boot's stock
     * {@code RestClient.Builder} then handles outbound traffic unmodified.
     */
    @SpringBootTest(properties = "ssrf.guard.enabled=false")
    static class WhenDisabled {

        @Autowired
        ApplicationContext applicationContext;

        @Test
        void safeDnsResolver_isNotRegistered() {
            assertThatThrownBy(() -> applicationContext.getBean(SafeDnsResolver.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        void interceptor_isNotRegistered() {
            assertThatThrownBy(() -> applicationContext.getBean(SsrfGuardInterceptor.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }
}
