package kr.devslab.ssrfguard.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Front-line {@link ClientHttpRequestInterceptor} that filters every outbound
 * {@code RestClient} request by scheme, host, and port <i>before</i> the
 * connection is opened.
 *
 * <p>Three checks, all string-level — no DNS:
 * <ul>
 *   <li>{@code scheme} must be in {@code allowedSchemes} (default {@code http}/{@code https}).</li>
 *   <li>{@code host} must be in either {@code exactHosts} or {@code suffixes}
 *       (see {@link NetUtil#hostMatches}).</li>
 *   <li>{@code port} must be in {@code allowedPorts}. {@code -1} stands for
 *       "URI omitted the port, scheme default applies" — leaving it in the
 *       set is what permits the common
 *       {@code https://api.example.com/...} (no explicit :443) form.</li>
 * </ul>
 *
 * <p>Anything that fails throws {@link SecurityException}. Pair this with
 * {@link SafeDnsResolver} for the IP-level check (private/loopback ranges)
 * and {@link SafeRedirectStrategy} for re-validation on redirect.
 */
public record SsrfGuardInterceptor(Iterable<String> exactHosts, Iterable<String> suffixes, Set<String> allowedSchemes,
                                   Set<Integer> allowedPorts) implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        URI uri = request.getURI();

        String scheme = uri.getScheme();
        if (scheme == null || allowedSchemes.stream().noneMatch(s -> s.equalsIgnoreCase(scheme))) {
            throw new SecurityException("Blocked scheme: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || !NetUtil.hostMatches(host, exactHosts, suffixes)) {
            throw new SecurityException("Host not allowed: " + host);
        }

        int port = uri.getPort(); // -1 = default
        if (!allowedPorts.contains(port)) {
            throw new SecurityException("Blocked port: " + port);
        }

        return execution.execute(request, body);
    }
}
