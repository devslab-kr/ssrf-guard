package kr.devslab.ssrfguard.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
}
