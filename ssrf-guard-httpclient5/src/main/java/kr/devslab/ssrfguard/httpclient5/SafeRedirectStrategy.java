package kr.devslab.ssrfguard.httpclient5;

import kr.devslab.ssrfguard.core.BlockReason;
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

    public SafeRedirectStrategy(SafeDnsResolver dnsResolver, Iterable<String> allowedSchemes, SsrfGuardMetrics metrics) {
        this.dnsResolver = dnsResolver;
        this.allowedSchemes = allowedSchemes;
        this.metrics = metrics;
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

        String host = location.getHost();
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
