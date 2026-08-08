package kr.devslab.ssrfguard.httpclient5;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.RedirectGuard;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Apache HttpClient {@link RedirectStrategy} that runs the same SSRF policy on
 * every redirect hop the underlying client would otherwise follow blindly.
 *
 * <p>The naive failure mode this strategy closes: an attacker whitelists
 * {@code example.com}, then has {@code https://example.com/redirect-me} return
 * a {@code 302 Location: http://169.254.169.254/...} pointing at AWS cloud
 * metadata. Without re-validation the client happily follows the redirect.
 *
 * <p>Implementation: delegate "is this even a redirect?" and "compute the
 * next URI" to {@link DefaultRedirectStrategy}, then push the resulting URI
 * back through scheme validation and through the same {@link SafeDnsResolver}
 * used on the first hop.
 */
public final class SafeRedirectStrategy implements RedirectStrategy {

    private static final Logger log = LoggerFactory.getLogger(SafeRedirectStrategy.class);

    private final DefaultRedirectStrategy delegate = new DefaultRedirectStrategy();
    private final SafeDnsResolver dnsResolver;
    private final Iterable<String> allowedSchemes;
    private final SsrfGuardMetrics metrics;
    private final UrlPolicy policy;

    /**
     * @param policy the same policy the first request is validated with. When
     *               {@code null} the hop falls back to the pre-3.2.0
     *               scheme-only check — kept so the older three-argument
     *               constructor keeps compiling, not because it is a good
     *               idea.
     */
    public SafeRedirectStrategy(SafeDnsResolver dnsResolver, Iterable<String> allowedSchemes,
                                SsrfGuardMetrics metrics, UrlPolicy policy) {
        this.dnsResolver = dnsResolver;
        this.allowedSchemes = allowedSchemes;
        this.metrics = metrics;
        this.policy = policy;
    }

    /**
     * @deprecated pass the {@link UrlPolicy} so redirect hops get the same
     *             checks as the first request. Without it, port, userinfo and
     *             IP-literal rules are not re-applied on a hop.
     */
    @Deprecated(since = "3.2.0")
    public SafeRedirectStrategy(SafeDnsResolver dnsResolver, Iterable<String> allowedSchemes, SsrfGuardMetrics metrics) {
        this(dnsResolver, allowedSchemes, metrics, null);
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return delegate.isRedirected(request, response, context);
    }

    @Override
    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        URI location;
        try {
            location = delegate.getLocationURI(request, response, context);
        } catch (Exception e) {
            throw new ProtocolException("Failed to get location URI: " + e.getMessage(), e);
        }

        String scheme = location.getScheme();
        String host = location.getHost();

        // The FULL policy, via the shared core seam — scheme, host, port,
        // userinfo and IP-literal, exactly what the first request got. This
        // used to check the scheme alone and leave the rest to the resolver,
        // so a hop to an allowlisted host on a blocked port, or to a public
        // IP literal, was followed. See RedirectGuard for why the decision
        // lives in core rather than here.
        if (policy != null) {
            try {
                RedirectGuard.validateHop(policy, location);
            } catch (SsrfGuardException e) {
                // UrlPolicy already recorded the metric and logged the rule.
                throw new RedirectException(e.getMessage());
            }
        } else {
            // Deprecated constructor path: scheme only, as before 3.2.0.
            boolean schemeAllowed = false;
            if (scheme != null) {
                for (String s : allowedSchemes) {
                    if (s.equalsIgnoreCase(scheme)) {
                        schemeAllowed = true;
                        break;
                    }
                }
            }
            if (!schemeAllowed) {
                recordBlocked(BlockReason.BLOCKED_REDIRECT, scheme, location.getHost());
                throw new RedirectException("Blocked redirect scheme: " + scheme);
            }
        }

        if (host == null) {
            recordBlocked(BlockReason.BLOCKED_REDIRECT, scheme, null);
            throw new RedirectException("Blocked redirect: empty host");
        }
        try {
            // Re-run the DNS resolver — its whitelist + private-IP filter is what
            // makes the redirect hop safe.
            var addrs = dnsResolver.resolve(host);
            if (addrs == null || addrs.length == 0) {
                recordBlocked(BlockReason.BLOCKED_REDIRECT, scheme, host);
                throw new RedirectException("Blocked redirect to host: " + host);
            }
        } catch (RedirectException re) {
            throw re;
        } catch (Exception e) {
            recordBlocked(BlockReason.BLOCKED_REDIRECT, scheme, host);
            log.warn("ssrf-guard: blocked redirect (host={}, scheme={}, cause={})", host, scheme, e.getMessage());
            throw new RedirectException("Blocked redirect to host: " + host + " cause: " + e.getMessage());
        }

        return location;
    }

    private void recordBlocked(BlockReason reason, String scheme, String host) {
        if (metrics != null) metrics.recordBlocked(reason, scheme, host);
    }
}
