package kr.devslab.ssrfguard.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the host/IP helpers. These are the building blocks every
 * other component leans on, so the matrix is wide on purpose — getting a
 * private-range classifier subtly wrong defeats the whole guard.
 */
class NetUtilTest {

    // ------------------------------------------------------------- //
    // hostMatches — whitelist semantics                              //
    // ------------------------------------------------------------- //

    @Test
    void hostMatches_exactHost_caseInsensitive() {
        assertThat(NetUtil.hostMatches("API.Example.com",
                List.of("api.example.com"), List.of()))
                .isTrue();
    }

    @Test
    void hostMatches_exactHost_rejectsDifferentHost() {
        assertThat(NetUtil.hostMatches("evil.com",
                List.of("api.example.com"), List.of()))
                .isFalse();
    }

    @Test
    void hostMatches_suffix_matchesItself() {
        assertThat(NetUtil.hostMatches("example.com",
                List.of(), List.of("example.com")))
                .isTrue();
    }

    @Test
    void hostMatches_suffix_matchesSubdomain() {
        assertThat(NetUtil.hostMatches("api.partner.example.com",
                List.of(), List.of("example.com")))
                .isTrue();
    }

    @Test
    void hostMatches_suffix_rejectsLookalikeAtBoundary() {
        // `badexample.com` ends with `example.com` as a string but NOT on a
        // domain-label boundary. This is the classic suffix-match attack —
        // hostMatches must reject it.
        assertThat(NetUtil.hostMatches("badexample.com",
                List.of(), List.of("example.com")))
                .isFalse();
    }

    @Test
    void hostMatches_emptyWhitelists_rejectsEverything() {
        assertThat(NetUtil.hostMatches("anything.com",
                List.of(), List.of()))
                .isFalse();
    }

    @Test
    void hostMatches_idnNormalised() {
        // Punycode form and the unicode form should match the same whitelist.
        assertThat(NetUtil.hostMatches("xn--caf-dma.example.com",
                List.of("café.example.com"), List.of()))
                .isTrue();
    }

    // ------------------------------------------------------------- //
    // isPrivateOrLocal — the IP filter                               //
    // ------------------------------------------------------------- //

    @Test
    void isPrivateOrLocal_loopback() throws Exception {
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("127.0.0.5"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("::1"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_rfc1918() throws Exception {
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("192.168.0.1"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_linkLocal_includesCloudMetadata() throws Exception {
        // The classic SSRF-on-AWS target.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("169.254.169.254"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_cgnatRange() throws Exception {
        // 100.64.0.0/10 — carrier-grade NAT. Not in JDK's siteLocalAddress.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("100.127.255.254"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_benchmarkRange() throws Exception {
        // 198.18.0.0/15 — RFC 2544 benchmarking. Not in JDK's siteLocalAddress.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("198.18.0.1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("198.19.255.254"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_broadcastAddress() throws Exception {
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("255.255.255.255"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_ipv6UniqueLocal() throws Exception {
        // fc00::/7 — IPv6 ULA.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fd00::1"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_ipv6LinkLocal() throws Exception {
        // fe80::/10 — IPv6 link-local.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fe80::1"))).isTrue();
    }

    @Test
    void isPrivateOrLocal_publicAddresses_areNotPrivate() throws Exception {
        // A sample of well-known public IPs that absolutely should NOT match.
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("1.1.1.1"))).isFalse();
        assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("9.9.9.9"))).isFalse();
    }

    // ------------------------------------------------------------- //
    // normalizeHost                                                  //
    // ------------------------------------------------------------- //

    @Test
    void normalizeHost_asciiPassesThrough() {
        assertThat(NetUtil.normalizeHost("api.example.com")).isEqualTo("api.example.com");
    }

    @Test
    void normalizeHost_idnConvertsToPunycode() {
        assertThat(NetUtil.normalizeHost("café.example.com")).isEqualTo("xn--caf-dma.example.com");
    }
}
