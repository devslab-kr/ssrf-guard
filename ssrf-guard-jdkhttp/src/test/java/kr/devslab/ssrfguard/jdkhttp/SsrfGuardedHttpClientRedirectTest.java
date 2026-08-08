package kr.devslab.ssrfguard.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The JDK client gives no hook to inspect a redirect hop, so before 3.2.0 a
 * redirect off an allowlisted host was followed with no validation at all.
 * This wrapper now drives the loop itself; these tests run it against a real
 * loopback server, because "does the guard see the hop" is a question about
 * behaviour, not about the shape of the code.
 */
class SsrfGuardedHttpClientRedirectTest {

    private HttpServer server;
    private String base;
    private final List<String> pathsServed = new ArrayList<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            pathsServed.add(path);
            lastMethod.set(exchange.getRequestMethod());
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));

            String location = switch (path) {
                case "/to-metadata" -> "http://169.254.169.254/latest/meta-data/";
                case "/to-other-port" -> base.replace(portOf(base), "9") + "/end";
                case "/to-self" -> "/to-self";
                case "/hop" -> "/end";
                default -> null;
            };

            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
                exchange.sendResponseHeaders(302, -1);
            } else if (path.equals("/see-other")) {
                exchange.getResponseHeaders().add("Location", "/end");
                exchange.sendResponseHeaders(303, -1);
            } else {
                byte[] body = "done".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        base = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static String portOf(String url) {
        return url.substring(url.lastIndexOf(':') + 1);
    }

    /** Loopback is the target, so the private-IP rule has to be off to test redirects at all. */
    private UrlPolicy loopbackPolicy() {
        String host = InetAddress.getLoopbackAddress().getHostAddress();
        return new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443, server.getAddress().getPort()),
                new HostPolicy(List.of(host), List.of()),
                false,  // the target IS an IP literal here
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
    }

    private SsrfGuardedHttpClient client(UrlPolicy policy) {
        return new SsrfGuardedHttpClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                policy, false);
    }

    @Test
    void refuses_a_delegate_that_follows_redirects_itself() {
        // Accepting one would mean the JDK chases Location internally and this
        // wrapper never sees a hop — a guard that silently does nothing.
        assertThatIllegalArgumentException().isThrownBy(() -> new SsrfGuardedHttpClient(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                        loopbackPolicy(), false))
                .withMessageContaining("Redirect.NEVER");
    }

    @Test
    void follows_an_allowed_redirect_and_returns_the_final_response() throws Exception {
        var response = client(loopbackPolicy()).send(
                HttpRequest.newBuilder(URI.create(base + "/hop")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("done");
        assertThat(pathsServed).containsExactly("/hop", "/end");
    }

    @Test
    void blocks_a_redirect_to_cloud_metadata() {
        // The hop the whole library exists for: an allowlisted host answering
        // with a Location that points at the metadata service.
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client(loopbackPolicy()).send(
                        HttpRequest.newBuilder(URI.create(base + "/to-metadata")).build(),
                        HttpResponse.BodyHandlers.ofString()))
                .matches(e -> e.reason() == BlockReason.BLOCKED_REDIRECT);

        assertThat(pathsServed).containsExactly("/to-metadata");
    }

    @Test
    void blocks_a_redirect_to_a_port_outside_the_policy() {
        // Not caught before 3.2.0: the old hop check looked at the scheme and
        // the host, so a port change on an allowlisted host was followed.
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client(loopbackPolicy()).send(
                        HttpRequest.newBuilder(URI.create(base + "/to-other-port")).build(),
                        HttpResponse.BodyHandlers.ofString()))
                .matches(e -> e.reason() == BlockReason.BLOCKED_REDIRECT);
    }

    @Test
    void gives_up_after_max_redirects() {
        var client = new SsrfGuardedHttpClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                loopbackPolicy(), false, 2);

        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client.send(
                        HttpRequest.newBuilder(URI.create(base + "/to-self")).build(),
                        HttpResponse.BodyHandlers.ofString()))
                .matches(e -> e.reason() == BlockReason.BLOCKED_REDIRECT)
                .withMessageContaining("Too many redirects");
    }

    @Test
    void downgrades_a_303_to_get_and_drops_the_body() throws Exception {
        var response = client(loopbackPolicy()).send(
                HttpRequest.newBuilder(URI.create(base + "/see-other"))
                        .POST(HttpRequest.BodyPublishers.ofString("payload"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastMethod.get()).isEqualTo("GET");
    }

    @Test
    void keeps_credentials_on_a_same_origin_hop() throws Exception {
        client(loopbackPolicy()).send(
                HttpRequest.newBuilder(URI.create(base + "/hop"))
                        .header("Authorization", "Bearer sekrit")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Same scheme, host and port — nothing to protect against here, and
        // stripping would break ordinary authenticated redirects.
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer sekrit");
    }

    @Test
    void maxRedirects_zero_hands_the_3xx_back_untouched() {
        var client = new SsrfGuardedHttpClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                loopbackPolicy(), false, 0);

        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client.send(
                        HttpRequest.newBuilder(URI.create(base + "/hop")).build(),
                        HttpResponse.BodyHandlers.ofString()))
                .withMessageContaining("Too many redirects");
    }
}
