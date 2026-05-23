package kr.devslab.ssrfguard.restclient;

import kr.devslab.ssrfguard.core.SsrfGuardProperties;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.httpclient5.SafeDnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies all SSRF Guard beans are registered when the autoconfig fires. */
@SpringBootTest(classes = TestApp.class)
@TestPropertySource(properties = {
        "ssrf.guard.enabled=true",
        // Set-bound property — comma list, not indexed.
        "ssrf.guard.allowed-ports=-1,80,443",
        "ssrf.guard.exact-hosts=api.example.com"
})
class SsrfGuardRestClientAutoConfigurationTest {

    @Autowired ApplicationContext ctx;
    @Autowired SafeDnsResolver dnsResolver;
    @Autowired CloseableHttpClient httpClient;
    @Autowired HttpComponentsClientHttpRequestFactory requestFactory;
    @Autowired SsrfGuardClientHttpRequestInterceptor interceptor;
    @Autowired UrlPolicy urlPolicy;
    @Autowired SsrfGuardProperties props;

    @Test
    void all_beans_are_registered() {
        assertThat(dnsResolver).isNotNull();
        assertThat(httpClient).isNotNull();
        assertThat(requestFactory).isNotNull();
        assertThat(interceptor).isNotNull();
        assertThat(urlPolicy).isNotNull();
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    void rest_client_customizer_is_registered_by_name() {
        // Spring Boot 3.5 already auto-registers a generic httpMessageConvertersRestClientCustomizer,
        // so we look up ours specifically by bean name.
        assertThat(ctx.containsBean("ssrfRestClientCustomizer")).isTrue();
        RestClientCustomizer ours = (RestClientCustomizer) ctx.getBean("ssrfRestClientCustomizer");
        assertThat(ours).isNotNull();
    }

    @Test
    void policy_picks_up_property_values() {
        assertThat(urlPolicy.allowedPorts()).containsExactlyInAnyOrder(-1, 80, 443);
        assertThat(urlPolicy.hostPolicy().exactHosts()).containsExactly("api.example.com");
    }
}
