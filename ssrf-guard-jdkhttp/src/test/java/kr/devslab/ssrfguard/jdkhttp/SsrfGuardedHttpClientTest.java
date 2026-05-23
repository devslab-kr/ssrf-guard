package kr.devslab.ssrfguard.jdkhttp;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SsrfGuardedHttpClientTest {

    private static UrlPolicy policy(List<String> exact) {
        return new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(exact, List.of()),
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
    }

    @Test
    void blocks_disallowed_host_before_any_network_io() {
        // delegate is a raw client — we don't expect it to be called.
        SsrfGuardedHttpClient safe = new SsrfGuardedHttpClient(
                HttpClient.newHttpClient(),
                policy(List.of("api.example.com")));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://evil.com/")).build();
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> safe.send(req, HttpResponse.BodyHandlers.ofString()))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        SsrfGuardedHttpClient safe = new SsrfGuardedHttpClient(
                HttpClient.newHttpClient(),
                policy(List.of("api.example.com")));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://2130706433/")).build();
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> safe.send(req, HttpResponse.BodyHandlers.ofString()))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }

    @Test
    void send_async_returns_failed_future_on_violation() throws Exception {
        SsrfGuardedHttpClient safe = new SsrfGuardedHttpClient(
                HttpClient.newHttpClient(),
                policy(List.of("api.example.com")));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://evil.com/")).build();
        var future = safe.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(future::get)
                .withCauseInstanceOf(SsrfGuardException.class);
    }

    @Test
    void delegate_is_exposed() {
        HttpClient real = HttpClient.newHttpClient();
        SsrfGuardedHttpClient safe = new SsrfGuardedHttpClient(real, policy(List.of("api.example.com")));
        assertThat(safe.delegate()).isSameAs(real);
    }
}
