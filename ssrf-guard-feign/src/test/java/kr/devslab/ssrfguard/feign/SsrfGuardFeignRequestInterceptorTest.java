package kr.devslab.ssrfguard.feign;

import feign.RequestTemplate;
import feign.Target;
import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SsrfGuardFeignRequestInterceptorTest {

    private static SsrfGuardFeignRequestInterceptor interceptor(List<String> exact) {
        UrlPolicy p = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(exact, List.of()),
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
        return new SsrfGuardFeignRequestInterceptor(p);
    }

    // Feign's RequestTemplate has a peculiar "feignTarget + url" composition;
    // the simplest way to drive it is to construct a HardCodedTarget and
    // attach it via reflection-free API: RequestTemplate.from(...) builds
    // one with the URL already resolved.
    private static RequestTemplate template(String fullUrl) {
        RequestTemplate t = new RequestTemplate();
        // Splitting around the path keeps Feign's internal "target.url + uri"
        // composition stable for the test.
        int schemeIdx = fullUrl.indexOf("://");
        int pathIdx = fullUrl.indexOf('/', schemeIdx + 3);
        String base = pathIdx > 0 ? fullUrl.substring(0, pathIdx) : fullUrl;
        String uri = pathIdx > 0 ? fullUrl.substring(pathIdx) : "/";
        t.feignTarget(new Target.HardCodedTarget<>(Object.class, base));
        t.uri(uri);
        return t;
    }

    @Test
    void permits_whitelisted_host() {
        SsrfGuardFeignRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatNoException().isThrownBy(() -> i.apply(template("https://api.example.com/v1")));
    }

    @Test
    void blocks_disallowed_host() {
        SsrfGuardFeignRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> i.apply(template("https://evil.com/")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        SsrfGuardFeignRequestInterceptor i = interceptor(List.of("api.example.com"));
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> i.apply(template("http://2130706433/")))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }
}
