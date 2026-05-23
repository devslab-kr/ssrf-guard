package kr.devslab.ssrfguard.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class UrlPolicyTest {

    private MeterRegistry registry;
    private MicrometerSsrfGuardMetrics metrics;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerSsrfGuardMetrics(registry);
    }

    private UrlPolicy policy(HostPolicy hostPolicy) {
        return new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                hostPolicy,
                true,
                true,
                metrics
        );
    }

    @Test
    void permits_whitelisted_https() {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        assertThatNoException().isThrownBy(() -> p.validate(URI.create("https://api.example.com/v1/data")));
        assertThat(registry.counter("ssrf_guard_allowed_total", "scheme", "https").count()).isEqualTo(1.0);
    }

    @Test
    void rejects_blocked_scheme() {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> p.validate(URI.create("file:///etc/passwd")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_SCHEME);
        assertThat(registry.counter("ssrf_guard_blocked_total", "reason", "blocked_scheme", "scheme", "file").count())
                .isEqualTo(1.0);
    }

    @Test
    void rejects_non_whitelisted_host() {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> p.validate(URI.create("https://evil.com/")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void rejects_blocked_port() {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> p.validate(URI.create("https://api.example.com:8080/")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_PORT);
    }

    // The high-value matrix: every obfuscated IP-literal form the attacker
    // might use to bypass a domain-name whitelist must be rejected.
    @DisplayName("Obfuscated IP literal forms are blocked when rejectIpLiteralHosts=true")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://0.0.0.0/",
            "http://2130706433/",        // 127.0.0.1 bare decimal
            "http://0x7f000001/",        // hex
            "http://0177.0.0.1/",        // octal
            "http://127.1/",             // short form
            "http://[::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://[fe80::1]/",
    })
    void rejects_ip_literal_form(String url) {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        URI uri = URI.create(url);
        assertThatExceptionOfType(SsrfGuardException.class)
                .as("expected %s to be blocked", url)
                .isThrownBy(() -> p.validate(uri))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL
                        || e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void rejects_userinfo() {
        UrlPolicy p = policy(new HostPolicy(List.of("api.example.com"), List.of()));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> p.validate(URI.create("https://user:pass@api.example.com/")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_USERINFO);
    }

    @Test
    void allows_userinfo_when_disabled() {
        UrlPolicy p = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(List.of("api.example.com"), List.of()),
                true,   // rejectIpLiteralHosts
                false,  // rejectUserInfo OFF
                metrics
        );
        assertThatNoException().isThrownBy(() ->
                p.validate(URI.create("https://user:pass@api.example.com/")));
    }

    @Test
    void allows_ip_literal_when_disabled_and_whitelisted() {
        // The escape hatch — user explicitly wants to allow a literal IP.
        UrlPolicy p = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(List.of("203.0.113.5"), List.of()),
                false,  // rejectIpLiteralHosts OFF
                true,
                metrics
        );
        assertThatNoException().isThrownBy(() -> p.validate(URI.create("http://203.0.113.5/")));
    }
}
