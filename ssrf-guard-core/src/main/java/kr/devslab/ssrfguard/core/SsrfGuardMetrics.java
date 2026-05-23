package kr.devslab.ssrfguard.core;

/**
 * Hook for surfacing SSRF Guard decisions to a metrics backend. The core
 * ships a {@link NoOpSsrfGuardMetrics} (zero-cost default) and a Micrometer
 * implementation that's auto-registered when Micrometer is on the classpath.
 *
 * <p>Implementations must be thread-safe — the same instance is shared by
 * every interceptor / filter / resolver across all HTTP clients in the JVM.
 */
public interface SsrfGuardMetrics {

    /**
     * Record a blocked request. Called from any guard component the moment
     * the decision is made — pre-DNS for scheme/host/port, post-DNS for
     * private-IP filtering, pre-redirect for redirect blocks.
     *
     * @param reason which rule fired (used as a metric tag)
     * @param scheme the URL scheme being blocked, or {@code null} if not yet parsed
     * @param host   the URL host being blocked, or {@code null}
     */
    void recordBlocked(BlockReason reason, String scheme, String host);

    /**
     * Record an allowed request — fires once per URL that passed every guard
     * gate. Useful for sanity-checking that the guard is actually being
     * exercised in production (e.g. {@code rate(ssrf_guard_allowed_total) > 0}).
     */
    void recordAllowed(String scheme, String host);
}
