package kr.devslab.ssrfguard.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApp.class)
@TestPropertySource(properties = {
        "ssrf.guard.enabled=true",
        "ssrf.guard.exact-hosts=api.example.com"
})
class SsrfGuardWebClientAutoConfigurationTest {

    @Autowired ApplicationContext ctx;
    @Autowired WebClient.Builder webClientBuilder;
    @Autowired SsrfGuardExchangeFilterFunction filter;
    // v3.1 — new connector + resolver beans wired by
    // ReactorNettyConnectorConfiguration. Should always resolve when
    // reactor-netty is on the classpath (which it is via spring-webflux).
    @Autowired ReactorClientHttpConnector connector;
    @Autowired SsrfGuardReactorAddressResolverGroup resolverGroup;

    @Test
    void filter_is_registered() {
        assertThat(filter).isNotNull();
    }

    @Test
    void web_client_customizer_is_registered() {
        assertThat(ctx.containsBean("ssrfWebClientCustomizer")).isTrue();
    }

    @Test
    void builder_is_available() {
        assertThat(webClientBuilder.build()).isNotNull();
    }

    @Test
    void reactor_client_http_connector_is_registered() {
        // v3.1 closes the DNS-time gap. The autoconfig should publish a
        // ReactorClientHttpConnector backed by our filtering resolver.
        assertThat(connector).isNotNull();
        assertThat(ctx.containsBean("ssrfReactorClientHttpConnector")).isTrue();
    }

    @Test
    void address_resolver_group_is_registered() {
        // The resolver group bean itself is also exposed so consumers with
        // their own ClientHttpConnector (custom pool / proxy / mTLS) can
        // still attach our resolver by calling httpClient.resolver(group).
        assertThat(resolverGroup).isNotNull();
        assertThat(ctx.containsBean("ssrfReactorAddressResolverGroup")).isTrue();
    }
}
