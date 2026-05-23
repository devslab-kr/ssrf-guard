package kr.devslab.ssrfguard.restclient;

import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Spring {@link ClientHttpRequestInterceptor} that delegates to
 * {@link UrlPolicy} for every outbound request. The interceptor runs before
 * any DNS lookup — this is the cheap front-line filter. Pair with
 * {@code SafeDnsResolver} (for DNS-time private-IP filtering) and
 * {@code SafeRedirectStrategy} (for redirect re-validation).
 *
 * <p>Works for both {@code RestClient} and {@code RestTemplate}: they share
 * the same {@link ClientHttpRequestInterceptor} type.
 */
public final class SsrfGuardClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final UrlPolicy policy;

    public SsrfGuardClientHttpRequestInterceptor(UrlPolicy policy) {
        this.policy = policy;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        policy.validate(request.getURI());
        return execution.execute(request, body);
    }

    public UrlPolicy policy() {
        return policy;
    }
}
