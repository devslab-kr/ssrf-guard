package kr.devslab.ssrfguard.resttemplate;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(classes = TestApp.class)
@TestPropertySource(properties = {
        "ssrf.guard.enabled=true",
        "ssrf.guard.exact-hosts=api.example.com"
})
class SsrfGuardRestTemplateAutoConfigurationTest {

    @Autowired RestTemplateBuilder builder;

    @Test
    void rest_template_blocks_disallowed_host() {
        RestTemplate rt = builder.build();
        // We don't actually need network — the interceptor throws before any
        // socket is opened.
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> rt.getForObject("https://evil.com/", String.class))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void rest_template_blocks_obfuscated_ip_literal() {
        RestTemplate rt = builder.build();
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> rt.getForObject("http://2130706433/", String.class))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }

    @Test
    void rest_template_has_at_least_one_interceptor() {
        RestTemplate rt = builder.build();
        assertThat(rt.getInterceptors()).isNotEmpty();
    }
}
