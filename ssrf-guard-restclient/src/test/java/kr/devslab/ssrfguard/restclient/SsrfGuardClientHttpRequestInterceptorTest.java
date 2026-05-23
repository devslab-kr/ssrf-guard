package kr.devslab.ssrfguard.restclient;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SsrfGuardClientHttpRequestInterceptorTest {

    private static SsrfGuardClientHttpRequestInterceptor interceptor(List<String> exactHosts) {
        UrlPolicy p = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(exactHosts, List.of()),
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
        return new SsrfGuardClientHttpRequestInterceptor(p);
    }

    private static HttpRequest req(String url) {
        return new HttpRequest() {
            @Override public HttpMethod getMethod() { return HttpMethod.GET; }
            @Override public URI getURI() { return URI.create(url); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
            @Override public java.util.Map<String, Object> getAttributes() { return java.util.Map.of(); }
        };
    }

    private static final ClientHttpRequestExecution NOOP = new ClientHttpRequestExecution() {
        @Override public ClientHttpResponse execute(HttpRequest request, byte[] body) throws IOException {
            return null; // policy.validate runs before this — null is fine if validation passes
        }
    };

    @Test
    void permits_whitelisted_https() {
        SsrfGuardClientHttpRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatNoException().isThrownBy(() -> i.intercept(req("https://api.example.com/v1"), new byte[0], NOOP));
    }

    @Test
    void blocks_non_https_when_scheme_not_allowed() {
        SsrfGuardClientHttpRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> i.intercept(req("ftp://api.example.com/"), new byte[0], NOOP))
                .matches(e -> e.reason() == BlockReason.BLOCKED_SCHEME);
    }

    @Test
    void blocks_unknown_host() {
        SsrfGuardClientHttpRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> i.intercept(req("https://evil.com/"), new byte[0], NOOP))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        SsrfGuardClientHttpRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> i.intercept(req("http://2130706433/"), new byte[0], NOOP))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }
}
