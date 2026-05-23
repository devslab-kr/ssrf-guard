package kr.devslab.ssrfguard.webclient;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpMethod.GET;

class SsrfGuardExchangeFilterFunctionTest {

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

    // A do-nothing next function — returns a successful response. Lets us
    // verify the filter calls through on permitted URIs and short-circuits
    // on blocked ones.
    private static final ExchangeFunction NEXT = request ->
            Mono.just(ClientResponse.create(HttpStatus.OK).build());

    @Test
    void permits_whitelisted_host() {
        SsrfGuardExchangeFilterFunction f = new SsrfGuardExchangeFilterFunction(policy(List.of("api.example.com")));
        ClientRequest req = ClientRequest.create(GET, URI.create("https://api.example.com/v1")).build();

        StepVerifier.create(f.filter(req, NEXT))
                .assertNext(resp -> {})
                .verifyComplete();
    }

    @Test
    void blocks_disallowed_host() {
        SsrfGuardExchangeFilterFunction f = new SsrfGuardExchangeFilterFunction(policy(List.of("api.example.com")));
        ClientRequest req = ClientRequest.create(GET, URI.create("https://evil.com/")).build();

        StepVerifier.create(f.filter(req, NEXT))
                .expectErrorMatches(e -> e instanceof SsrfGuardException sg && sg.reason() == BlockReason.BLOCKED_HOST)
                .verify();
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        SsrfGuardExchangeFilterFunction f = new SsrfGuardExchangeFilterFunction(policy(List.of("api.example.com")));
        ClientRequest req = ClientRequest.create(GET, URI.create("http://2130706433/")).build();

        StepVerifier.create(f.filter(req, NEXT))
                .expectErrorMatches(e -> e instanceof SsrfGuardException sg && sg.reason() == BlockReason.BLOCKED_IP_LITERAL)
                .verify();
    }

    @Test
    void blocks_userinfo() {
        SsrfGuardExchangeFilterFunction f = new SsrfGuardExchangeFilterFunction(policy(List.of("api.example.com")));
        ClientRequest req = ClientRequest.create(GET, URI.create("https://user:pass@api.example.com/")).build();

        StepVerifier.create(f.filter(req, NEXT))
                .expectErrorMatches(e -> e instanceof SsrfGuardException sg && sg.reason() == BlockReason.BLOCKED_USERINFO)
                .verify();
    }
}
