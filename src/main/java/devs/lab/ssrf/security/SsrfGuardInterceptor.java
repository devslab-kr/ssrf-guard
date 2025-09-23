package devs.lab.ssrf.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

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
            // 기본포트(-1) 허용 또는 명시 포트가 80/443 등만 허용됨
            throw new SecurityException("Blocked port: " + port);
        }

        return execution.execute(request, body);
    }
}