package kr.devslab.ssrfguard.webclient;

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.NetUtil;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * DNS-time gate for the reactor-netty {@code HttpClient} that backs Spring's
 * {@link org.springframework.web.reactive.function.client.WebClient}. Wraps
 * the JDK's default {@link AddressResolverGroup} and filters out any
 * resolved {@link InetSocketAddress} whose IP lands in the private /
 * loopback / link-local / metadata / CGNAT ranges.
 *
 * <p>This closes the v3.0.0 follow-up gap: the existing
 * {@link SsrfGuardExchangeFilterFunction} only validates the URL <i>string</i>
 * (scheme / host / port / IP-literal / userinfo) before the request leaves.
 * The DNS step happens afterwards inside reactor-netty, and at that point a
 * host that passed the whitelist could still resolve to {@code 169.254.169.254}
 * (DNS rebinding) or to a private RFC-1918 address. Hooking the address
 * resolver lets us re-check at the IP level — the same defense-in-depth
 * the RestClient module gets through Apache HttpClient's {@code DnsResolver}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Delegate to the JDK resolver.</b> {@link DefaultAddressResolverGroup}
 *       — same DNS behaviour the consumer would have without us — then
 *       filter results.</li>
 *   <li><b>Filter, don't fail per-address.</b> A host with multiple A
 *       records keeps any public ones; only fails if <i>every</i> resolved
 *       IP is private.</li>
 *   <li><b>Block-private toggle.</b> Mirrors {@code ssrf.guard.block-private-networks}
 *       — same default-on, same property name.</li>
 *   <li><b>Metrics integration.</b> Records {@link BlockReason#BLOCKED_PRIVATE_IP}
 *       blocks through the configured {@link SsrfGuardMetrics} so the same
 *       {@code ssrf_guard_blocked_total} counter the RestClient module
 *       populates also reflects WebClient blocks.</li>
 * </ul>
 *
 * <h2>What this does NOT cover</h2>
 * The URL-time check (scheme / whitelist / port / IP literal / userinfo)
 * stays in {@link SsrfGuardExchangeFilterFunction} — that runs <i>before</i>
 * DNS, so attacks like {@code http://2130706433/} get rejected at the
 * filter and never even reach the resolver. The resolver is the
 * second-defense layer for whitelisted hosts that resolve to a private IP.
 */
public final class SsrfGuardReactorAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuardReactorAddressResolverGroup.class);

    private final AddressResolverGroup<InetSocketAddress> delegate;
    private final boolean blockPrivate;
    private final SsrfGuardMetrics metrics;

    public SsrfGuardReactorAddressResolverGroup(boolean blockPrivate, SsrfGuardMetrics metrics) {
        this(DefaultAddressResolverGroup.INSTANCE, blockPrivate, metrics);
    }

    /** Test seam — allow injecting a non-default delegate group. */
    public SsrfGuardReactorAddressResolverGroup(
            AddressResolverGroup<InetSocketAddress> delegate,
            boolean blockPrivate,
            SsrfGuardMetrics metrics) {
        this.delegate = delegate;
        this.blockPrivate = blockPrivate;
        this.metrics = (metrics == null) ? NoOpSsrfGuardMetrics.INSTANCE : metrics;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        AddressResolver<InetSocketAddress> raw = delegate.getResolver(executor);
        return new FilteringAddressResolver(executor, raw, blockPrivate, metrics);
    }

    /**
     * Netty {@link AbstractAddressResolver} that delegates real DNS work to
     * the JDK resolver and applies the private-IP filter on results.
     *
     * <p>Both {@link #doResolve} (single) and {@link #doResolveAll} (all
     * answers) are hooked — reactor-netty calls one or the other depending
     * on whether multi-A-record fail-over is enabled.
     */
    private static final class FilteringAddressResolver extends AbstractAddressResolver<InetSocketAddress> {

        private final AddressResolver<InetSocketAddress> delegate;
        private final boolean blockPrivate;
        private final SsrfGuardMetrics metrics;

        FilteringAddressResolver(EventExecutor executor,
                                 AddressResolver<InetSocketAddress> delegate,
                                 boolean blockPrivate,
                                 SsrfGuardMetrics metrics) {
            super(executor);
            this.delegate = delegate;
            this.blockPrivate = blockPrivate;
            this.metrics = metrics;
        }

        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return delegate.isResolved(address);
        }

        @Override
        protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) {
            // ASYNC. doResolve returns; the delegate's future invokes our
            // listener on completion, and we set the promise from there.
            delegate.resolve(unresolved).addListener((Future<InetSocketAddress> future) -> {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }
                InetSocketAddress resolved = future.getNow();
                if (blockPrivate && NetUtil.isPrivateOrLocal(resolved.getAddress())) {
                    recordBlock(unresolved.getHostString(), resolved);
                    promise.setFailure(new UnknownHostException(
                            "ssrf-guard: blocked private/loopback IP at DNS resolution — " +
                                    resolved.getAddress().getHostAddress()));
                } else {
                    promise.setSuccess(resolved);
                }
            });
        }

        @Override
        protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise) {
            delegate.resolveAll(unresolved).addListener((Future<List<InetSocketAddress>> future) -> {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }
                List<InetSocketAddress> raw = future.getNow();
                if (!blockPrivate) {
                    promise.setSuccess(raw);
                    return;
                }
                List<InetSocketAddress> filtered = new ArrayList<>(raw.size());
                for (InetSocketAddress addr : raw) {
                    if (!NetUtil.isPrivateOrLocal(addr.getAddress())) {
                        filtered.add(addr);
                    }
                }
                if (filtered.isEmpty()) {
                    recordBlock(unresolved.getHostString(), raw.isEmpty() ? null : raw.get(0));
                    promise.setFailure(new UnknownHostException(
                            "ssrf-guard: blocked — all resolved IPs are private/loopback for host " +
                                    unresolved.getHostString()));
                } else {
                    promise.setSuccess(filtered);
                }
            });
        }

        private void recordBlock(String host, InetSocketAddress firstSeen) {
            // Best-effort. NoOp metrics in pure non-Spring use; Micrometer
            // counter when the autoconfig wires the real bean.
            try {
                metrics.recordBlocked(BlockReason.BLOCKED_PRIVATE_IP, null, host);
            } catch (RuntimeException ignored) {
                // metric backends shouldn't break the resolver path
            }
            log.warn("ssrf-guard: blocked WebClient DNS — host={} resolved to private/loopback (first={})",
                    host, firstSeen == null ? null : firstSeen.getAddress().getHostAddress());
        }
    }
}
