package kr.devslab.ssrfguard.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Configuration for SSRF Guard — exposed under the {@code ssrf.guard}
 * prefix in {@code application.yml} / {@code application.properties}.
 *
 * <p>Whitelist semantics: a host is allowed iff it matches at least one of
 * {@link #exactHosts} (case-insensitive, IDN-normalised) or one of
 * {@link #suffixes} (full domain match or any of its subdomains). Anything
 * else is rejected before the request reaches the network.
 *
 * <p>Defense in depth: the interceptor checks scheme/host/port up-front
 * <i>and</i> rejects obfuscated IP literals + userinfo; the DNS resolver
 * (in the {@code httpclient5} module) re-checks the host AND filters
 * private/loopback/link-local IPs (under {@link #blockPrivateNetworks});
 * when {@link #followRedirects} is true, every redirect hop is re-validated
 * through the same DNS resolver. Together these close the TOCTOU window a
 * naive string-only whitelist would leave open.
 */
@Data
@ConfigurationProperties(prefix = "ssrf.guard")
public class SsrfGuardProperties {

    /**
     * Master switch — when false no SSRF Guard beans are registered and
     * Spring Boot's stock {@code RestClient.Builder} / {@code RestTemplate} /
     * {@code WebClient} are used as-is. Default: true.
     */
    private boolean enabled = true;

    /**
     * URI schemes the guard will permit. Default: {@code http, https}.
     * Set to a smaller subset (e.g. {@code [https]}) to forbid plain HTTP.
     */
    private Set<String> allowedSchemes = Set.of("http", "https");

    /**
     * TCP ports the guard will permit. {@code -1} stands for the scheme's
     * default port (i.e. 80 for http, 443 for https) — leaving it in the set
     * means "URIs that omit an explicit port are OK." Default: {@code [-1, 80, 443]}.
     */
    private Set<Integer> allowedPorts = Set.of(-1, 80, 443);

    /**
     * Exact-match host whitelist. Comparison is case-insensitive and IDN-
     * normalised, so {@code Café.example.com} matches {@code café.example.com}
     * matches {@code xn--caf-dma.example.com}.
     */
    private List<String> exactHosts = List.of();

    /**
     * Suffix-match host whitelist. The match is on a domain boundary, so
     * {@code partner.example.com} matches itself, {@code api.partner.example.com},
     * and any other subdomain — but NOT {@code badpartner.example.com}.
     */
    private List<String> suffixes = List.of();

    /**
     * Reject any DNS result that resolves to a private, loopback, link-local,
     * site-local, or multicast address. This is what blocks the classic
     * {@code http://169.254.169.254/...} (cloud metadata) and
     * {@code http://10.0.0.5/...} (internal services) attacks even when the
     * requested hostname looks innocent. Default: true.
     */
    private boolean blockPrivateNetworks = true;

    /**
     * Reject any URL whose host parses as an IP literal in any form (dotted
     * decimal, bare decimal, hex, partial, or IPv6). Most production
     * whitelists are domain names; flipping this on closes the obfuscated-
     * IP bypass class (e.g. {@code http://2130706433/} → {@code 127.0.0.1}).
     * Turn off only if you intentionally allow specific IP-literal hosts.
     * Default: true.
     */
    private boolean rejectIpLiteralHosts = true;

    /**
     * Reject any URL containing userinfo ({@code user:pass@host/...}). Modern
     * apps rarely send credentials in URLs (they go in {@code Authorization}
     * headers); blocking userinfo closes log-injection and basic-auth-leak
     * vectors as a side benefit. Default: true.
     */
    private boolean rejectUserInfo = true;

    /** Socket connect timeout. Default: 5s. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Socket read timeout. Default: 10s. */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * Whether to follow HTTP 3xx redirects. When true each hop is re-validated
     * with the same scheme/host/IP rules as the original request, so an
     * attacker can't whitelist {@code example.com} and redirect to
     * {@code 169.254.169.254}. Default: true.
     */
    private boolean followRedirects = true;
}
