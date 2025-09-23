package devs.lab.ssrf.security;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

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
        // 이 반환값이 실제 소켓 연결에 사용됨 → 검증=연결 일치(TOCTOU 완화)
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