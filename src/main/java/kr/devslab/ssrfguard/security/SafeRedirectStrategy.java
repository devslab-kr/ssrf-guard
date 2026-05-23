package kr.devslab.ssrfguard.security;

import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.net.URI;

/**
 * Apache HttpClient {@link RedirectStrategy} that runs the same SSRF policy on
 * every redirect hop the underlying client would otherwise follow blindly.
 *
 * <p>The naive failure mode the strategy closes: an attacker whitelists
 * {@code example.com}, then has {@code https://example.com/redirect-me} return
 * a {@code 302 Location: http://169.254.169.254/...} pointing at AWS cloud
 * metadata. Without re-validation the client happily follows the redirect.
 *
 * <p>Implementation: delegate the "is this even a redirect?" and
 * "compute the next URI" decisions to {@link DefaultRedirectStrategy}, then
 * push the resulting URI back through scheme validation and through the same
 * {@link SafeDnsResolver} used on the first hop. The DNS resolver already
 * does whitelist + private-IP filtering, so anything passing this check is on
 * the same footing as the original request.
 */
public class SafeRedirectStrategy implements RedirectStrategy {

    private final DefaultRedirectStrategy delegate = new DefaultRedirectStrategy();
    private final SafeDnsResolver dnsResolver;
    private final Iterable<String> allowedSchemes;

    public SafeRedirectStrategy(SafeDnsResolver dnsResolver, Iterable<String> allowedSchemes) {
        this.dnsResolver = dnsResolver;
        this.allowedSchemes = allowedSchemes;
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
        if (!schemeAllowed) throw new RedirectException("Blocked redirect scheme: " + scheme);

        String host = location.getHost();
        if (host == null) throw new RedirectException("Blocked redirect: empty host");
        try {
            // Re-run the DNS resolver — its whitelist + private-IP filter is what
            // makes the redirect hop safe.
            var addrs = dnsResolver.resolve(host);
            if (addrs == null || addrs.length == 0) {
                throw new RedirectException("Blocked redirect to host: " + host);
            }
        } catch (Exception e) {
            throw new RedirectException("Blocked redirect to host: " + host + " cause: " + e.getMessage());
        }

        return location;
    }
}
