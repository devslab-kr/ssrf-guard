package kr.devslab.ssrfguard.okhttp;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NetUtil;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import okhttp3.Dns;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * OkHttp {@link Dns} implementation — the DNS-time gate that mirrors what
 * {@code SafeDnsResolver} does for Apache HttpClient 5.
 *
 * <ol>
 *   <li>Refuse to resolve hosts not in the whitelist.</li>
 *   <li>Filter out any resolved IP that's private/loopback/link-local/
 *       metadata. Empty result → {@link UnknownHostException}, which
 *       OkHttp surfaces as a clean connect failure.</li>
 * </ol>
 *
 * <p>Like the HttpClient 5 resolver, this closes the TOCTOU window: OkHttp
 * connects to whatever we return here, so validate=connect consistency is
 * guaranteed.
 */
public final class SsrfGuardOkHttpDns implements Dns {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuardOkHttpDns.class);

    private final HostPolicy hostPolicy;
    private final boolean blockPrivate;
    private final SsrfGuardMetrics metrics;

    public SsrfGuardOkHttpDns(HostPolicy hostPolicy, boolean blockPrivate, SsrfGuardMetrics metrics) {
        this.hostPolicy = hostPolicy;
        this.blockPrivate = blockPrivate;
        this.metrics = (metrics == null) ? NoOpSsrfGuardMetrics.INSTANCE : metrics;
    }

    public SsrfGuardOkHttpDns(HostPolicy hostPolicy, boolean blockPrivate) {
        this(hostPolicy, blockPrivate, NoOpSsrfGuardMetrics.INSTANCE);
    }

    @NotNull
    @Override
    public List<InetAddress> lookup(@NotNull String hostname) throws UnknownHostException {
        String normalized = NetUtil.normalizeHost(hostname);
        if (!hostPolicy.allows(normalized)) {
            metrics.recordBlocked(BlockReason.BLOCKED_HOST, null, hostname);
            log.warn("ssrf-guard: blocked OkHttp DNS — host not in whitelist (host={})", hostname);
            throw new UnknownHostException("Host not in whitelist: " + normalized);
        }
        InetAddress[] addrs = InetAddress.getAllByName(normalized);
        List<InetAddress> filtered = Arrays.stream(addrs)
                .filter(a -> !blockPrivate || !NetUtil.isPrivateOrLocal(a))
                .toList();
        if (filtered.isEmpty()) {
            metrics.recordBlocked(BlockReason.BLOCKED_PRIVATE_IP, null, hostname);
            log.warn("ssrf-guard: blocked OkHttp DNS — all resolved IPs are private (host={})", hostname);
            throw new UnknownHostException("No allowed IP after filtering: " + normalized);
        }
        return Collections.unmodifiableList(filtered);
    }
}
