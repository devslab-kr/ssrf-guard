package devs.lab.ssrf.security;

import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.net.URI;

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
                if (s.equalsIgnoreCase(scheme)) { schemeAllowed = true; break; }
            }
        }
        if (!schemeAllowed) throw new RedirectException("Blocked redirect scheme: " + scheme);

        String host = location.getHost();
        if (host == null) throw new RedirectException("Blocked redirect: empty host");
        try {
            // hop마다 동일 정책으로 재검증
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