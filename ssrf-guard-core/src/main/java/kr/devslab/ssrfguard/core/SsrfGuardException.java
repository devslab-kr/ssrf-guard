package kr.devslab.ssrfguard.core;

/**
 * Thrown when SSRF Guard rejects an outbound HTTP request. Extends
 * {@link SecurityException} so the v2.x catch sites still work — anything
 * that was catching {@code SecurityException} will continue to match.
 *
 * <p>The {@link BlockReason} surfaces the precise rule that fired, which is
 * what metrics + structured logs use as a tag. The {@link #host()} is the
 * raw host string from the URL (not normalised), useful for log correlation.
 */
public class SsrfGuardException extends SecurityException {

    private static final long serialVersionUID = 1L;

    private final BlockReason reason;
    private final String host;
    private final String scheme;

    public SsrfGuardException(BlockReason reason, String scheme, String host, String message) {
        super(message);
        this.reason = reason;
        this.scheme = scheme;
        this.host = host;
    }

    public BlockReason reason() {
        return reason;
    }

    public String host() {
        return host;
    }

    public String scheme() {
        return scheme;
    }
}
