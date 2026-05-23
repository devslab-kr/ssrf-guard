package kr.devslab.ssrfguard.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Micrometer-backed {@link SsrfGuardMetrics}. Emits two counters:
 *
 * <pre>{@code
 * ssrf_guard_blocked_total{reason="blocked_private_ip", scheme="http"} 42
 * ssrf_guard_allowed_total{scheme="https"} 13042
 * }</pre>
 *
 * <p><b>Why {@code scheme} but not {@code host} is a tag.</b> Host is high-
 * cardinality — every URL the app calls becomes a unique tag value, which
 * blows up Prometheus storage and dashboards alike. Host is logged via
 * SLF4J instead, where it doesn't cardinality-explode. Scheme is naturally
 * bounded to ~3 values (http, https, occasionally ws).
 */
public final class MicrometerSsrfGuardMetrics implements SsrfGuardMetrics {

    private static final String METRIC_BLOCKED = "ssrf_guard_blocked_total";
    private static final String METRIC_ALLOWED = "ssrf_guard_allowed_total";

    private final MeterRegistry registry;

    public MicrometerSsrfGuardMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordBlocked(BlockReason reason, String scheme, String host) {
        Tags tags = Tags.of(
                Tag.of("reason", reason.label()),
                Tag.of("scheme", tagSafe(scheme))
        );
        Counter.builder(METRIC_BLOCKED)
                .description("Number of outbound HTTP requests blocked by SSRF Guard.")
                .tags(tags)
                .register(registry)
                .increment();
    }

    @Override
    public void recordAllowed(String scheme, String host) {
        Counter.builder(METRIC_ALLOWED)
                .description("Number of outbound HTTP requests allowed through SSRF Guard.")
                .tags(Tags.of(Tag.of("scheme", tagSafe(scheme))))
                .register(registry)
                .increment();
    }

    /** {@code null}/empty schemes become {@code "unknown"} — Prometheus rejects empty values. */
    private static String tagSafe(String s) {
        return (s == null || s.isEmpty()) ? "unknown" : s.toLowerCase();
    }
}
