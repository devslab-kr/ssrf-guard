package kr.devslab.ssrfguard.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * Front-line URL validator. Applies scheme + IP-literal + userinfo + host +
 * port checks against a URI <i>before</i> any DNS lookup happens — the
 * cheapest gate in the pipeline, designed to reject obvious attempts
 * instantly.
 *
 * <p>The DNS-time IP filter ({@code httpclient5} module's
 * {@code SafeDnsResolver}) is the second gate; on a redirect it runs a third
 * time via {@code SafeRedirectStrategy}.
 *
 * <p>Every reject path calls into {@link SsrfGuardMetrics} so consumers can
 * graph what's being blocked, then throws {@link SsrfGuardException} with
 * a {@link BlockReason} tag.
 */
public final class UrlPolicy {

    private static final Logger log = LoggerFactory.getLogger(UrlPolicy.class);

    private final Set<String> allowedSchemes;
    private final Set<Integer> allowedPorts;
    private final HostPolicy hostPolicy;
    private final boolean rejectIpLiteralHosts;
    private final boolean rejectUserInfo;
    private final SsrfGuardMetrics metrics;

    public UrlPolicy(
            Set<String> allowedSchemes,
            Set<Integer> allowedPorts,
            HostPolicy hostPolicy,
            boolean rejectIpLiteralHosts,
            boolean rejectUserInfo,
            SsrfGuardMetrics metrics
    ) {
        this.allowedSchemes = lowercase(allowedSchemes);
        this.allowedPorts = Set.copyOf(allowedPorts);
        this.hostPolicy = hostPolicy;
        this.rejectIpLiteralHosts = rejectIpLiteralHosts;
        this.rejectUserInfo = rejectUserInfo;
        this.metrics = (metrics == null) ? NoOpSsrfGuardMetrics.INSTANCE : metrics;
    }

    public Set<String> allowedSchemes() {
        return allowedSchemes;
    }

    public Set<Integer> allowedPorts() {
        return allowedPorts;
    }

    public HostPolicy hostPolicy() {
        return hostPolicy;
    }

    public boolean rejectIpLiteralHosts() {
        return rejectIpLiteralHosts;
    }

    public boolean rejectUserInfo() {
        return rejectUserInfo;
    }

    public SsrfGuardMetrics metrics() {
        return metrics;
    }

    /**
     * Validate a URI. Throws {@link SsrfGuardException} on the first failing
     * check; records a metric and a WARN log either way. Does not perform
     * any DNS lookup.
     */
    public void validate(URI uri) throws SsrfGuardException {
        String scheme = uri == null ? null : uri.getScheme();
        String host = uri == null ? null : uri.getHost();

        if (rejectUserInfo && uri != null && uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            reject(BlockReason.BLOCKED_USERINFO, scheme, host,
                    "URL must not contain userinfo (user:pass@) — blocked as a known SSRF bypass vector");
        }

        if (scheme == null || !allowedSchemes.contains(scheme.toLowerCase())) {
            reject(BlockReason.BLOCKED_SCHEME, scheme, host, "Blocked scheme: " + scheme);
        }

        if (host == null || host.isEmpty()) {
            reject(BlockReason.BLOCKED_HOST, scheme, host, "URL is missing a host");
        }

        if (rejectIpLiteralHosts && NetUtil.looksLikeIpLiteral(host)) {
            reject(BlockReason.BLOCKED_IP_LITERAL, scheme, host,
                    "IP-literal host blocked (rejectIpLiteralHosts=true): " + host);
        }

        if (!hostPolicy.allows(host)) {
            reject(BlockReason.BLOCKED_HOST, scheme, host, "Host not allowed: " + host);
        }

        int port = uri.getPort();
        if (!allowedPorts.contains(port)) {
            reject(BlockReason.BLOCKED_PORT, scheme, host, "Blocked port: " + port);
        }

        metrics.recordAllowed(scheme, host);
    }

    private void reject(BlockReason reason, String scheme, String host, String message) {
        metrics.recordBlocked(reason, scheme, host);
        // WARN here is the right level: this fires whenever the app tries to
        // call a URL the policy refuses. Operationally that's either a bug
        // (whitelist too narrow) or an attack — both worth surfacing.
        log.warn("ssrf-guard: {} (reason={}, scheme={}, host={})",
                message, reason.label(), scheme, host);
        throw new SsrfGuardException(reason, scheme, host, message);
    }

    private static Set<String> lowercase(Set<String> in) {
        if (in == null) return Set.of();
        java.util.Set<String> out = new java.util.HashSet<>(in.size());
        for (String s : in) {
            if (s != null) out.add(s.toLowerCase());
        }
        return Set.copyOf(out);
    }
}
