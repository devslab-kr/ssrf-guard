package kr.devslab.ssrfguard.okhttp;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SsrfGuardOkHttpInterceptorTest {

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

    @Test
    void blocks_disallowed_host_via_interceptor() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new SsrfGuardOkHttpInterceptor(policy(List.of("api.example.com"))))
                .build();
        Request req = new Request.Builder().url("https://evil.com/").build();
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client.newCall(req).execute())
                .matches(e -> e.reason() == BlockReason.BLOCKED_HOST);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new SsrfGuardOkHttpInterceptor(policy(List.of("api.example.com"))))
                .build();
        Request req = new Request.Builder().url("http://2130706433/").build();
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client.newCall(req).execute())
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }

    @Test
    void blocks_userinfo() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new SsrfGuardOkHttpInterceptor(policy(List.of("api.example.com"))))
                .build();
        Request req = new Request.Builder().url("https://user:pass@api.example.com/").build();
        // OkHttp's interceptor chain re-throws unchecked exceptions verbatim
        // (it only wraps IOException). SsrfGuardException extends
        // SecurityException → unchecked → surfaces directly.
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> client.newCall(req).execute())
                .matches(e -> e.reason() == BlockReason.BLOCKED_USERINFO);
    }
}
