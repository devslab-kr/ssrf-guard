package kr.devslab.ssrfguard.webclient;

import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Reactive {@link ExchangeFilterFunction} that runs the SSRF policy against
 * every outbound request URI <i>before</i> the underlying reactor-netty (or
 * Jetty Reactive, or whatever) client opens a socket.
 *
 * <p>Policy violation produces a {@link Mono#error}-wrapped
 * {@link SsrfGuardException}; consumers see it as a regular reactive error
 * that flows through their {@code onErrorResume} / {@code retry} operators
 * just like any other exception.
 *
 * <p><b>What this filter does NOT do.</b> The DNS-time private-IP check —
 * the second defense layer for the blocking RestClient — needs a custom
 * resolver hooked into reactor-netty's connection provider. The URL-time
 * checks here cover the IP-literal bypass class; the private-IP gap is
 * tracked as a follow-up (see WebClientDnsConfigurer in this module for the
 * extension point).
 */
public final class SsrfGuardExchangeFilterFunction implements ExchangeFilterFunction {

    private final UrlPolicy policy;

    public SsrfGuardExchangeFilterFunction(UrlPolicy policy) {
        this.policy = policy;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        try {
            policy.validate(request.url());
        } catch (SsrfGuardException e) {
            return Mono.error(e);
        }
        return next.exchange(request);
    }

    public UrlPolicy policy() {
        return policy;
    }
}
