package kr.devslab.ssrfguard.okhttp;

import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SsrfGuardOkHttpDnsTest {

    @Test
    void blocks_non_whitelisted() {
        SsrfGuardOkHttpDns dns = new SsrfGuardOkHttpDns(
                new HostPolicy(List.of("api.example.com"), List.of()),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> dns.lookup("evil.com"))
                .withMessageContaining("Host not in whitelist");
    }

    @Test
    void blocks_localhost_with_private_filter() {
        SsrfGuardOkHttpDns dns = new SsrfGuardOkHttpDns(
                new HostPolicy(List.of("localhost"), List.of()),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> dns.lookup("localhost"))
                .withMessageContaining("No allowed IP after filtering");
    }

    @Test
    void allows_localhost_when_filter_off() throws Exception {
        SsrfGuardOkHttpDns dns = new SsrfGuardOkHttpDns(
                new HostPolicy(List.of("localhost"), List.of()),
                false);
        assertThat(dns.lookup("localhost")).isNotEmpty();
    }
}
