package kr.devslab.ssrfguard.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the front-line interceptor. We use Spring's
 * {@link MockClientHttpRequest} so the assertions stay focused on the
 * string-level validation logic — no real HTTP, no DNS.
 */
class SsrfGuardInterceptorTest {

    private final SsrfGuardInterceptor interceptor = new SsrfGuardInterceptor(
            List.of("api.example.com"),
            List.of("partner.com"),
            Set.of("http", "https"),
            Set.of(-1, 80, 443)
    );

    @Test
    void accepts_exactHost_defaultPort() throws IOException {
        var execution = new RecordingExecution();
        interceptor.intercept(req(URI.create("https://api.example.com/orders")), new byte[0], execution);
        assertThat(execution.executed).isTrue();
    }

    @Test
    void accepts_suffixSubdomain() throws IOException {
        var execution = new RecordingExecution();
        interceptor.intercept(req(URI.create("https://team.partner.com/")), new byte[0], execution);
        assertThat(execution.executed).isTrue();
    }

    @Test
    void rejects_nonWhitelistedHost() {
        assertThatThrownBy(() -> interceptor.intercept(
                req(URI.create("https://evil.com/")), new byte[0], new RecordingExecution()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Host not allowed");
    }

    @Test
    void rejects_blockedScheme() {
        assertThatThrownBy(() -> interceptor.intercept(
                req(URI.create("file:///etc/passwd")), new byte[0], new RecordingExecution()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Blocked scheme");
    }

    @Test
    void rejects_blockedPort() {
        // Explicit port 8080 is not in the allow-list.
        assertThatThrownBy(() -> interceptor.intercept(
                req(URI.create("https://api.example.com:8080/")), new byte[0], new RecordingExecution()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Blocked port");
    }

    @Test
    void rejects_lookalikeSuffixAtBoundary() {
        // badpartner.com ends with partner.com but not on a label boundary.
        assertThatThrownBy(() -> interceptor.intercept(
                req(URI.create("https://badpartner.com/")), new byte[0], new RecordingExecution()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Host not allowed");
    }

    @Test
    void accepts_httpWithDefaultPort() throws IOException {
        var execution = new RecordingExecution();
        interceptor.intercept(req(URI.create("http://api.example.com/")), new byte[0], execution);
        assertThat(execution.executed).isTrue();
    }

    @Test
    void accepts_explicitDefaultPort80() throws IOException {
        var execution = new RecordingExecution();
        interceptor.intercept(req(URI.create("http://api.example.com:80/")), new byte[0], execution);
        assertThat(execution.executed).isTrue();
    }

    @Test
    void accepts_explicitDefaultPort443() throws IOException {
        var execution = new RecordingExecution();
        interceptor.intercept(req(URI.create("https://api.example.com:443/")), new byte[0], execution);
        assertThat(execution.executed).isTrue();
    }

    // ------------------------------------------------------------- //
    // Test fixtures                                                  //
    // ------------------------------------------------------------- //

    private static MockClientHttpRequest req(URI uri) {
        return new MockClientHttpRequest(HttpMethod.GET, uri);
    }

    /** Tracks whether the chain proceeded past the interceptor. */
    private static final class RecordingExecution implements ClientHttpRequestExecution {
        boolean executed = false;

        @Override
        public ClientHttpResponse execute(org.springframework.http.HttpRequest request, byte[] body) {
            executed = true;
            return null;
        }
    }
}
