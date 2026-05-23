package kr.devslab.ssrfguard.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerSsrfGuardMetricsTest {

    @Test
    void increments_blocked_counter_with_tags() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SsrfGuardMetrics m = new MicrometerSsrfGuardMetrics(registry);

        m.recordBlocked(BlockReason.BLOCKED_HOST, "https", "evil.com");
        m.recordBlocked(BlockReason.BLOCKED_HOST, "https", "evil.com");
        m.recordBlocked(BlockReason.BLOCKED_PRIVATE_IP, "https", "evil.com");

        assertThat(registry.counter("ssrf_guard_blocked_total", "reason", "blocked_host", "scheme", "https").count())
                .isEqualTo(2.0);
        assertThat(registry.counter("ssrf_guard_blocked_total", "reason", "blocked_private_ip", "scheme", "https").count())
                .isEqualTo(1.0);
    }

    @Test
    void increments_allowed_counter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SsrfGuardMetrics m = new MicrometerSsrfGuardMetrics(registry);
        m.recordAllowed("https", "api.example.com");
        m.recordAllowed("https", "api.example.com");
        assertThat(registry.counter("ssrf_guard_allowed_total", "scheme", "https").count()).isEqualTo(2.0);
    }

    @Test
    void null_scheme_becomes_unknown_tag() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SsrfGuardMetrics m = new MicrometerSsrfGuardMetrics(registry);
        m.recordAllowed(null, "anything");
        m.recordBlocked(BlockReason.BLOCKED_SCHEME, null, "anything");
        // Prometheus rejects empty tag values; SimpleMeterRegistry doesn't, but
        // the tag-safe normalisation should still show up.
        assertThat(registry.counter("ssrf_guard_allowed_total", "scheme", "unknown").count()).isEqualTo(1.0);
        assertThat(registry.counter("ssrf_guard_blocked_total", "reason", "blocked_scheme", "scheme", "unknown").count())
                .isEqualTo(1.0);
    }

    @Test
    void noop_metrics_are_safe() {
        // Pure smoke test — must not throw.
        NoOpSsrfGuardMetrics.INSTANCE.recordBlocked(BlockReason.BLOCKED_HOST, "https", "x");
        NoOpSsrfGuardMetrics.INSTANCE.recordAllowed("https", "x");
    }
}
