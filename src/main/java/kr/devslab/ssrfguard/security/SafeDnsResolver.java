package kr.devslab.ssrfguard.security;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Apache HttpClient {@link DnsResolver} that doubles as the SSRF defense's
 * last gate before the socket is opened. Two responsibilities:
 *
 * <ol>
 *   <li><b>Whitelist re-check.</b> The interceptor already validated the host,
 *       but a redirect target wouldn't have run through it — and an attacker
 *       who tricks {@code InetAddress.getAllByName} into returning a different
 *       set than the interceptor saw also fails here.</li>
 *   <li><b>IP filtering.</b> When {@code blockPrivate} is true, any address
 *       that lands inside loopback / private / link-local / multicast /
 *       cloud-metadata ranges is stripped from the returned array. If nothing
 *       is left the resolution throws {@link UnknownHostException}, which
 *       HttpClient surfaces as a connection failure rather than a redirect or
 *       partial connect.</li>
 * </ol>
 *
 * <p><b>TOCTOU note.</b> The array this resolver returns is what HttpClient
 * passes to {@code Socket.connect()}; the same {@link InetAddress} instances
 * are reused, so there is no second DNS lookup between validation and
 * connection. That's the time-of-check-time-of-use window closed.
 */
public record SafeDnsResolver(List<String> exactHosts, List<String> suffixes,
                              boolean blockPrivate) implements DnsResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        String normalized = NetUtil.normalizeHost(host);
        if (!NetUtil.hostMatches(normalized, exactHosts, suffixes)) {
            throw new UnknownHostException("Host not in whitelist: " + normalized);
        }
        InetAddress[] addrs = InetAddress.getAllByName(normalized);
        InetAddress[] filtered = Arrays.stream(addrs)
                .filter(a -> !blockPrivate || !NetUtil.isPrivateOrLocal(a))
                .toArray(InetAddress[]::new);

        if (filtered.length == 0) {
            throw new UnknownHostException("No allowed IP after filtering: " + normalized);
        }
        // Returned array is what HttpClient hands to Socket.connect — validate=connect
        // consistency closes the TOCTOU window.
        return filtered;
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
        return DnsResolver.super.resolve(host, port);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return InetAddress.getByName(host).getCanonicalHostName();
    }
}
