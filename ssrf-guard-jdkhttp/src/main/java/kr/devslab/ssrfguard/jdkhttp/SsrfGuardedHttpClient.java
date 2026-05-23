package kr.devslab.ssrfguard.jdkhttp;

import kr.devslab.ssrfguard.core.NetUtil;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps a JDK {@link HttpClient} so every outbound request is validated
 * against an {@link UrlPolicy} and (optionally) DNS-filtered for
 * private/loopback IPs <i>before</i> the underlying client opens a socket.
 *
 * <p>The JDK client doesn't have an interceptor extension point — there's no
 * equivalent to Apache HttpClient's {@code DnsResolver} or RestClient's
 * interceptor list. So this class is a small static-typed wrapper: same
 * method shape (synchronous {@code send}, async {@code sendAsync}), just
 * with the policy check inserted at the top.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * HttpClient real = HttpClient.newHttpClient();
 * SsrfGuardedHttpClient safe = new SsrfGuardedHttpClient(real, urlPolicy);
 *
 * HttpResponse<String> resp = safe.send(
 *     HttpRequest.newBuilder(URI.create("https://api.example.com/")).build(),
 *     HttpResponse.BodyHandlers.ofString());
 * }</pre>
 *
 * <h2>What's NOT closed by this wrapper</h2>
 * The JDK client uses {@code InetAddress.getAllByName} internally; we don't
 * intercept that call. So a DNS rebinding attack <i>could</i> in theory
 * change the resolved IP between our pre-flight {@link #checkDns(String)}
 * and the actual socket connect. The window is much smaller than for
 * non-pinned clients (the JDK client doesn't re-query DNS for every send),
 * but it's not zero. Consumers who need tight TOCTOU closure should use
 * the {@code ssrf-guard-httpclient5} module instead, where the wrapped
 * DnsResolver guarantees the validated IP is what gets connected to.
 */
public final class SsrfGuardedHttpClient {

    private final HttpClient delegate;
    private final UrlPolicy policy;
    private final boolean blockPrivateNetworks;

    public SsrfGuardedHttpClient(HttpClient delegate, UrlPolicy policy) {
        this(delegate, policy, true);
    }

    public SsrfGuardedHttpClient(HttpClient delegate, UrlPolicy policy, boolean blockPrivateNetworks) {
        this.delegate = delegate;
        this.policy = policy;
        this.blockPrivateNetworks = blockPrivateNetworks;
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        guard(request);
        return delegate.send(request, handler);
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            guard(request);
        } catch (SsrfGuardException e) {
            return CompletableFuture.failedFuture(e);
        }
        return delegate.sendAsync(request, handler);
    }

    public HttpClient delegate() {
        return delegate;
    }

    public UrlPolicy policy() {
        return policy;
    }

    private void guard(HttpRequest request) {
        policy.validate(request.uri());
        String host = request.uri().getHost();
        if (host != null && blockPrivateNetworks) {
            checkDns(host);
        }
    }

    private void checkDns(String host) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(NetUtil.normalizeHost(host));
            for (InetAddress a : addrs) {
                if (NetUtil.isPrivateOrLocal(a)) {
                    throw new SsrfGuardException(
                            kr.devslab.ssrfguard.core.BlockReason.BLOCKED_PRIVATE_IP,
                            null, host,
                            "DNS resolved to a private/loopback address: " + a.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            // Let the JDK client surface DNS errors on its own terms.
        }
    }
}
