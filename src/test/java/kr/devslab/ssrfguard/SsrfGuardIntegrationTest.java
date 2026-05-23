package kr.devslab.ssrfguard;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test that drives real HTTP through a {@link RestClient} the
 * starter customised. The auto-config wires {@link MockWebServer}'s host into
 * the whitelist (via {@code @DynamicPropertySource}), so well-formed requests
 * to the mock should succeed and anything outside the whitelist should be
 * rejected before the network is touched.
 *
 * <p>{@code blockPrivateNetworks=false} for the happy path because
 * MockWebServer binds to {@code localhost} (loopback) — leaving the filter on
 * would (correctly) refuse to connect.
 */
@SpringBootTest(properties = {
        "ssrf.guard.enabled=true",
        "ssrf.guard.block-private-networks=false"
})
class SsrfGuardIntegrationTest {

    static final MockWebServer mockServer;

    static {
        mockServer = new MockWebServer();
        try {
            mockServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start MockWebServer", e);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Set<Integer>/List<String> binding via @SpringBootTest properties
        // requires comma-separated form (indexed [0]/[1] syntax only works
        // for List backed by an explicit index in YAML). Putting the whole
        // list in one @DynamicPropertySource entry keeps it Set-compatible
        // and lets us slot the MockWebServer's ephemeral port in.
        //
        // MockWebServer's hostName isn't always literal "localhost" —
        // Docker Desktop hosts pick it up as "kubernetes.docker.internal",
        // various other dev setups return "127.0.0.1", etc. Read the actual
        // hostname so the whitelist matches whatever the OS gave us.
        registry.add("ssrf.guard.suffixes", mockServer::getHostName);
        registry.add("ssrf.guard.allowed-ports",
                () -> "-1,80,443," + mockServer.getPort());
    }

    @AfterAll
    static void shutdown() throws IOException {
        mockServer.shutdown();
    }

    @Autowired
    RestClient.Builder restClientBuilder;

    @Test
    void whitelistedHost_succeeds() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        RestClient client = restClientBuilder.build();

        String body = client.get()
                .uri(mockServer.url("/health").uri())
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("ok");
    }

    @Test
    void nonWhitelistedHost_isBlocked() {
        RestClient client = restClientBuilder.build();

        // example.com is not on the suffix whitelist for this test.
        // The interceptor throws SecurityException directly — RestClient
        // doesn't wrap it (it only wraps IOException-family failures into
        // ResourceAccessException), so we assert on it head-on.
        assertThatThrownBy(() -> client.get()
                .uri("https://example.com/")
                .retrieve()
                .body(String.class))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Host not allowed");
    }

    @Test
    void blockedScheme_isRejected() {
        RestClient client = restClientBuilder.build();

        // file:// scheme not in allowed-schemes (defaults to http/https).
        assertThatThrownBy(() -> client.get()
                .uri("file:///etc/passwd")
                .retrieve()
                .body(String.class))
                .isInstanceOf(Exception.class);
    }
}
