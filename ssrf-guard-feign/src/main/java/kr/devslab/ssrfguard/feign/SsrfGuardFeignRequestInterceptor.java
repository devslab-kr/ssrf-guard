package kr.devslab.ssrfguard.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import kr.devslab.ssrfguard.core.UrlPolicy;

import java.net.URI;

/**
 * Feign {@link RequestInterceptor} that delegates to {@link UrlPolicy} for
 * every outbound Feign call. Runs before the HTTP layer dispatches the
 * request, so policy violations short-circuit by throwing
 * {@code SsrfGuardException} (a {@link SecurityException}).
 *
 * <p>Feign exposes the resolved URL on the {@link RequestTemplate} only
 * after path-template substitution and load-balancer interception, so the
 * URI we inspect here is the actual host the underlying client would dial.
 */
public final class SsrfGuardFeignRequestInterceptor implements RequestInterceptor {

    private final UrlPolicy policy;

    public SsrfGuardFeignRequestInterceptor(UrlPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void apply(RequestTemplate template) {
        String url = template.feignTarget() != null && template.url() != null
                ? template.feignTarget().url() + template.url()
                : template.url();
        if (url == null || url.isEmpty()) {
            return;   // nothing to validate — Feign hasn't finished resolving
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            // Malformed URL — let Feign reject it on its own terms, not ours.
            return;
        }
        policy.validate(uri);
    }

    public UrlPolicy policy() {
        return policy;
    }
}
