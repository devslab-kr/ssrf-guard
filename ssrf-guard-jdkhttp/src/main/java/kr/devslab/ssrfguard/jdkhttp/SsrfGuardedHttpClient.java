package kr.devslab.ssrfguard.jdkhttp;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.NetUtil;
import kr.devslab.ssrfguard.core.RedirectGuard;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
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
 * <h2>Redirects</h2>
 * The JDK client follows redirects internally when built with
 * {@code Redirect.NORMAL} or {@code ALWAYS}, and gives no hook to inspect a
 * hop — so before 3.2.0 a redirect off an allowlisted host was followed with
 * NO validation at all. This wrapper therefore drives the redirect loop
 * itself: the delegate is used with following disabled and each hop is
 * re-validated through {@link RedirectGuard}, the same seam the other
 * adapters use.
 *
 * <p>Semantics follow the fetch specification, matching the JS sibling:
 * {@code 303} (and {@code 301}/{@code 302} on {@code POST}) downgrade to
 * {@code GET} and drop the body, and credential headers are stripped when a
 * hop crosses an origin. {@code maxRedirects} bounds the chain.
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

    /** Chain bound, matching the JS sibling's `maxRedirects` default. */
    public static final int DEFAULT_MAX_REDIRECTS = 5;

    private final HttpClient delegate;
    private final UrlPolicy policy;
    private final boolean blockPrivateNetworks;
    private final int maxRedirects;

    public SsrfGuardedHttpClient(HttpClient delegate, UrlPolicy policy) {
        this(delegate, policy, true);
    }

    public SsrfGuardedHttpClient(HttpClient delegate, UrlPolicy policy, boolean blockPrivateNetworks) {
        this(delegate, policy, blockPrivateNetworks, DEFAULT_MAX_REDIRECTS);
    }

    /**
     * @param maxRedirects hops to follow before giving up. {@code 0} refuses
     *                     to follow any redirect and hands the 3xx back.
     */
    public SsrfGuardedHttpClient(HttpClient delegate, UrlPolicy policy, boolean blockPrivateNetworks,
                                 int maxRedirects) {
        if (maxRedirects < 0) throw new IllegalArgumentException("maxRedirects must be >= 0");
        // The guarantee this class makes is that every hop is validated. It
        // cannot make that guarantee over a delegate that follows redirects
        // itself: the JDK client would chase the Location header internally
        // and this wrapper would never see the 3xx. Refusing here is loud;
        // accepting it would be a guard that quietly does nothing on the one
        // request shape SSRF is actually about.
        if (delegate.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException(
                    "SsrfGuardedHttpClient requires a delegate built with "
                            + "HttpClient.Redirect.NEVER (got " + delegate.followRedirects() + "). "
                            + "This wrapper follows and re-validates redirects itself; a delegate "
                            + "that follows them internally would bypass the policy on every hop.");
        }
        this.delegate = delegate;
        this.policy = policy;
        this.blockPrivateNetworks = blockPrivateNetworks;
        this.maxRedirects = maxRedirects;
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpRequest current = request;
        for (int hop = 0; ; hop++) {
            guard(current);
            HttpResponse<T> response = delegate.send(current, handler);
            HttpRequest next = nextHop(current, response, hop);
            if (next == null) return response;
            current = next;
        }
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            guard(request);
        } catch (SsrfGuardException e) {
            return CompletableFuture.failedFuture(e);
        }
        return delegate.sendAsync(request, handler)
                .thenCompose(response -> {
                    HttpRequest next;
                    try {
                        next = nextHop(request, response, 0);
                    } catch (SsrfGuardException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    if (next == null) return CompletableFuture.completedFuture(response);
                    return sendAsyncFrom(next, handler, 1);
                });
    }

    private <T> CompletableFuture<HttpResponse<T>> sendAsyncFrom(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler,
                                                                 int hop) {
        try {
            guard(request);
        } catch (SsrfGuardException e) {
            return CompletableFuture.failedFuture(e);
        }
        return delegate.sendAsync(request, handler)
                .thenCompose(response -> {
                    HttpRequest next;
                    try {
                        next = nextHop(request, response, hop);
                    } catch (SsrfGuardException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    if (next == null) return CompletableFuture.completedFuture(response);
                    return sendAsyncFrom(next, handler, hop + 1);
                });
    }

    /**
     * Build the next hop's request, or {@code null} when the response is not
     * a redirect this client should follow. Every hop passes the full policy
     * through {@link RedirectGuard} before it is returned, so a caller can
     * never send a request this method produced without it having been
     * validated.
     */
    private HttpRequest nextHop(HttpRequest current, HttpResponse<?> response, int hop) {
        int status = response.statusCode();
        if (status < 300 || status >= 400 || status == 304) return null;

        Optional<String> location = response.headers().firstValue("location");
        // A 3xx with no Location is not a redirect anyone can follow — hand it
        // back rather than inventing a target.
        if (location.isEmpty() || location.get().isBlank()) return null;

        if (hop >= maxRedirects) {
            throw new SsrfGuardException(BlockReason.BLOCKED_REDIRECT, current.uri().getScheme(),
                    current.uri().getHost(), "Too many redirects: " + maxRedirects);
        }

        URI target;
        try {
            target = current.uri().resolve(location.get().trim());
        } catch (IllegalArgumentException e) {
            throw new SsrfGuardException(BlockReason.BLOCKED_REDIRECT, current.uri().getScheme(),
                    current.uri().getHost(), "Blocked redirect: unparseable location");
        }
        RedirectGuard.validateHop(policy, target);

        String method = current.method();
        boolean toGet = RedirectGuard.downgradesToGet(status, method);
        boolean crossOrigin = RedirectGuard.crossOrigin(current.uri(), target);

        HttpRequest.Builder builder = HttpRequest.newBuilder(target);
        current.timeout().ifPresent(builder::timeout);
        current.version().ifPresent(builder::version);
        current.headers().map().forEach((name, values) -> {
            // Content-* headers describe a body that is not being replayed,
            // and credentials must not cross an origin.
            if (toGet && name.toLowerCase(java.util.Locale.ROOT).startsWith("content-")) return;
            if (crossOrigin && RedirectGuard.isCredentialHeader(name)) return;
            for (String value : values) builder.header(name, value);
        });

        if (toGet) {
            builder.GET();
        } else {
            builder.method(method, current.bodyPublisher().orElseGet(HttpRequest.BodyPublishers::noBody));
        }
        return builder.build();
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
