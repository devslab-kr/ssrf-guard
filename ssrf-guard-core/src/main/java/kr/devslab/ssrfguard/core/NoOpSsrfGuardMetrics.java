package kr.devslab.ssrfguard.core;

/**
 * Default {@link SsrfGuardMetrics} used when Micrometer is not on the
 * classpath. Discards every event — picked at autoconfig time so the
 * core works in environments that don't want a metrics dep.
 */
public final class NoOpSsrfGuardMetrics implements SsrfGuardMetrics {

    public static final NoOpSsrfGuardMetrics INSTANCE = new NoOpSsrfGuardMetrics();

    @Override
    public void recordBlocked(BlockReason reason, String scheme, String host) {
        // intentionally empty
    }

    @Override
    public void recordAllowed(String scheme, String host) {
        // intentionally empty
    }
}
