package kr.devslab.ssrfguard.webclient;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the DNS-filtering address resolver against a stub upstream that
 * returns a fixed answer set — covers the public IP, private IP, and
 * mixed-resolution cases without depending on real DNS.
 */
class SsrfGuardReactorAddressResolverGroupTest {

    private static NioEventLoopGroup eventLoop;

    @BeforeAll
    static void startEventLoop() {
        eventLoop = new NioEventLoopGroup(1);
    }

    @AfterAll
    static void stopEventLoop() throws Exception {
        eventLoop.shutdownGracefully().await(2, TimeUnit.SECONDS);
    }

    /** Stub group that always resolves the unresolved host to a fixed list. */
    private static AddressResolverGroup<InetSocketAddress> stubGroup(InetSocketAddress... answers) {
        return new AddressResolverGroup<>() {
            @Override
            protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
                return new AbstractAddressResolver<>(executor, InetSocketAddress.class) {
                    @Override
                    protected boolean doIsResolved(InetSocketAddress address) {
                        return !address.isUnresolved();
                    }

                    @Override
                    protected void doResolve(InetSocketAddress unresolved, Promise<InetSocketAddress> promise) {
                        promise.setSuccess(answers[0]);
                    }

                    @Override
                    protected void doResolveAll(InetSocketAddress unresolved, Promise<List<InetSocketAddress>> promise) {
                        promise.setSuccess(List.of(answers));
                    }
                };
            }
        };
    }

    private static InetSocketAddress addr(String ip, int port) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(ip), port);
    }

    @Test
    void public_ip_passes_through_single_resolve() throws Exception {
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("8.8.8.8", 443)),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<InetSocketAddress> future = resolver.resolve(InetSocketAddress.createUnresolved("dns.google", 443))
                .await();

        assertThat(future.isSuccess()).isTrue();
        assertThat(future.getNow().getAddress().getHostAddress()).isEqualTo("8.8.8.8");
    }

    @Test
    void private_ip_fails_single_resolve() throws Exception {
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("127.0.0.1", 80)),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<InetSocketAddress> future = resolver.resolve(InetSocketAddress.createUnresolved("localhost", 80))
                .await();

        assertThat(future.isSuccess()).isFalse();
        assertThat(future.cause())
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("private/loopback");
    }

    @Test
    void aws_metadata_link_local_blocked() throws Exception {
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("169.254.169.254", 80)),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<InetSocketAddress> future = resolver.resolve(InetSocketAddress.createUnresolved("metadata.attacker", 80))
                .await();

        assertThat(future.isSuccess()).isFalse();
        assertThat(future.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void resolveAll_filters_private_keeps_public() throws Exception {
        // Multi-A scenario: host resolves to one public + one private IP.
        // Should succeed with only the public IP returned.
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("8.8.8.8", 443), addr("10.0.0.5", 443)),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<List<InetSocketAddress>> future = resolver.resolveAll(
                InetSocketAddress.createUnresolved("mixed.example.com", 443)).await();

        assertThat(future.isSuccess()).isTrue();
        assertThat(future.getNow()).hasSize(1);
        assertThat(future.getNow().get(0).getAddress().getHostAddress()).isEqualTo("8.8.8.8");
    }

    @Test
    void resolveAll_fails_when_all_private() throws Exception {
        // Multi-A scenario: every IP is private. Should fail entirely
        // rather than connecting to one of them.
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("10.0.0.5", 443), addr("192.168.1.1", 443)),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<List<InetSocketAddress>> future = resolver.resolveAll(
                InetSocketAddress.createUnresolved("all-private.example.com", 443)).await();

        assertThat(future.isSuccess()).isFalse();
        assertThat(future.cause()).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void filtering_disabled_passes_through() throws Exception {
        // block-private-networks=false → private IPs pass.
        var group = new SsrfGuardReactorAddressResolverGroup(
                stubGroup(addr("10.0.0.5", 80)),
                false,
                NoOpSsrfGuardMetrics.INSTANCE);
        AddressResolver<InetSocketAddress> resolver = group.getResolver(eventLoop.next());

        Future<InetSocketAddress> future = resolver.resolve(InetSocketAddress.createUnresolved("internal.corp", 80))
                .await();

        assertThat(future.isSuccess()).isTrue();
        assertThat(future.getNow().getAddress().getHostAddress()).isEqualTo("10.0.0.5");
    }
}
