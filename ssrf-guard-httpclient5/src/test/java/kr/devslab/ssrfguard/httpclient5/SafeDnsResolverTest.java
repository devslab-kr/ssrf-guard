package kr.devslab.ssrfguard.httpclient5;

import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SafeDnsResolverTest {

    @Test
    void blocks_host_not_in_whitelist() {
        SafeDnsResolver dns = new SafeDnsResolver(
                new HostPolicy(List.of("api.example.com"), List.of()),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);

        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> dns.resolve("evil.com"))
                .withMessageContaining("Host not in whitelist");
    }

    @Test
    void blocks_localhost_when_block_private_is_on() {
        // localhost is in whitelist, but resolves to a loopback address —
        // the private-IP filter should reject it.
        SafeDnsResolver dns = new SafeDnsResolver(
                new HostPolicy(List.of("localhost"), List.of()),
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        assertThatExceptionOfType(UnknownHostException.class)
                .isThrownBy(() -> dns.resolve("localhost"))
                .withMessageContaining("No allowed IP after filtering");
    }

    @Test
    void allows_localhost_when_block_private_is_off() throws Exception {
        SafeDnsResolver dns = new SafeDnsResolver(
                new HostPolicy(List.of("localhost"), List.of()),
                false,
                NoOpSsrfGuardMetrics.INSTANCE);
        assertThat(dns.resolve("localhost")).isNotEmpty();
    }
}
