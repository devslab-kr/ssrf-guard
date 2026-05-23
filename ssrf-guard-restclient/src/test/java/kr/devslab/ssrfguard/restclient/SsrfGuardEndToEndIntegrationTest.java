package kr.devslab.ssrfguard.restclient;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.SsrfGuardException;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * End-to-end test that exercises the full pipeline against a real HTTP
 * server (MockWebServer):
 *   - URL-time policy (scheme/host/port/IP-literal)
 *   - DNS-time private-IP filter (skipped here — MockWebServer is loopback,
 *     so block-private-networks is disabled for the test)
 *   - Redirect re-validation (the cloud-metadata bypass class)
 *
 * <p>Single static MockWebServer lifecycle: brought up once in the static
 * initializer (so @DynamicPropertySource can see its port at context
 * refresh) and torn down in @AfterAll.
 */
@SpringBootTest(classes = TestApp.class)
class SsrfGuardEndToEndIntegrationTest {

    private static final MockWebServer mockServer = new MockWebServer();

    static {
        try {
            mockServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ssrf.guard.enabled", () -> "true");
        registry.add("ssrf.guard.block-private-networks", () -> "false");  // MockWebServer is loopback
        registry.add("ssrf.guard.exact-hosts", () -> mockServer.getHostName());
        registry.add("ssrf.guard.allowed-ports", () -> mockServer.getPort() + ",-1,80,443");
    }

    @Autowired RestClient.Builder restClientBuilder;

    private RestClient client() {
        return restClientBuilder.build();
    }

    private String mockUrl(String path) {
        return "http://" + mockServer.getHostName() + ":" + mockServer.getPort() + path;
    }

    @Test
    void permits_whitelisted_request_against_real_server() {
        mockServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));
        String body = client().get()
                .uri(mockUrl("/"))
                .retrieve()
                .body(String.class);
        assertThat(body).isEqualTo("ok");
    }

    @Test
    void blocks_request_to_disallowed_host() {
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client().get()
                        .uri("https://evil.com/")
                        .retrieve()
                        .body(String.class))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void blocks_redirect_to_disallowed_host() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.com/"));

        // Apache HttpClient surfaces redirect failures as IOException → Spring
        // wraps in ResourceAccessException. Both shapes are acceptable so long
        // as the redirect doesn't succeed.
        try {
            client().get()
                    .uri(mockUrl("/redirect"))
                    .retrieve()
                    .body(String.class);
            // If we got here, the redirect was followed — that's the bug.
            throw new AssertionError("expected redirect to be blocked");
        } catch (ResourceAccessException expected) {
            assertThat(expected.getMessage()).containsAnyOf("redirect", "Redirect", "Blocked");
        } catch (SsrfGuardException expected) {
            // RestClient may surface the SSRF exception directly if the
            // redirect target gets re-validated by the URL policy instead of
            // failing at the DNS layer.
            assertThat(expected.reason()).isIn(BlockReason.BLOCKED_REDIRECT, BlockReason.BLOCKED_HOST);
        }
    }

    @Test
    void blocks_obfuscated_ip_literal_end_to_end() {
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client().get()
                        .uri("http://2130706433/")
                        .retrieve()
                        .body(String.class))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }
}
