package kr.devslab.ssrfguard.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class NetUtilTest {

    @Nested
    @DisplayName("hostMatches — exact + suffix whitelist semantics")
    class HostMatches {

        @Test
        void exact_host_matches_case_insensitive() {
            assertThat(NetUtil.hostMatches("API.partner.com", List.of("api.partner.com"), List.of())).isTrue();
            assertThat(NetUtil.hostMatches("api.partner.com", List.of("API.partner.com"), List.of())).isTrue();
        }

        @Test
        void idn_normalises_both_sides() {
            assertThat(NetUtil.hostMatches("café.example.com", List.of("xn--caf-dma.example.com"), List.of())).isTrue();
            assertThat(NetUtil.hostMatches("CAFÉ.example.com", List.of("café.example.com"), List.of())).isTrue();
        }

        @Test
        void suffix_matches_subdomain_on_label_boundary() {
            assertThat(NetUtil.hostMatches("api.partner.example.com", List.of(), List.of("example.com"))).isTrue();
            assertThat(NetUtil.hostMatches("example.com", List.of(), List.of("example.com"))).isTrue();
        }

        @Test
        void suffix_does_not_match_non_boundary() {
            // badexample.com ends with "example.com" but not on a label boundary
            assertThat(NetUtil.hostMatches("badexample.com", List.of(), List.of("example.com"))).isFalse();
            assertThat(NetUtil.hostMatches("example.com.evil.tld", List.of(), List.of("example.com"))).isFalse();
        }

        @Test
        void trailing_dot_does_not_break_match() {
            // Absolute-FQDN form "example.com." is equivalent to "example.com"
            assertThat(NetUtil.hostMatches("example.com.", List.of("example.com"), List.of())).isTrue();
            assertThat(NetUtil.hostMatches("api.partner.example.com.", List.of(), List.of("example.com"))).isTrue();
        }

        @Test
        void empty_whitelists_match_nothing() {
            assertThat(NetUtil.hostMatches("anything.example.com", List.of(), List.of())).isFalse();
        }

        @Test
        void null_host_does_not_explode() {
            assertThat(NetUtil.hostMatches(null, List.of("example.com"), List.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("isPrivateOrLocal — IPv4 classification")
    class PrivateIpv4 {

        @ParameterizedTest(name = "{0} is private/local")
        @ValueSource(strings = {
                "127.0.0.1",            // loopback
                "127.255.255.254",      // entire 127.0.0.0/8
                "10.0.0.1", "10.255.255.255",     // RFC 1918
                "172.16.0.1", "172.31.255.255",   // RFC 1918
                "192.168.0.1", "192.168.255.255", // RFC 1918
                "169.254.0.1", "169.254.169.254", // link-local incl. AWS metadata
                "100.64.0.1", "100.127.255.255",  // CGNAT
                "198.18.0.1", "198.19.255.255",   // benchmark
                "0.0.0.0",              // wildcard
                "255.255.255.255",      // broadcast
                "224.0.0.1",            // multicast
        })
        void blocked(String ip) throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName(ip)))
                    .as("expected %s to be classified as private/local", ip)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0} is public")
        @ValueSource(strings = {
                "8.8.8.8",      // Google DNS
                "1.1.1.1",      // Cloudflare
                "13.107.6.152", // Microsoft
                "172.217.0.0",  // public, just outside 172.16/12
                "192.0.2.1",    // TEST-NET-1 — RFC 5737, technically "documentation" but routable
        })
        void allowed(String ip) throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName(ip)))
                    .as("expected %s to be classified as public", ip)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("isPrivateOrLocal — IPv6 classification incl. IPv4-mapped bypass")
    class PrivateIpv6 {

        @Test
        void loopback() throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("::1"))).isTrue();
        }

        @Test
        void link_local() throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fe80::1"))).isTrue();
        }

        @Test
        void unique_local() throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fc00::1"))).isTrue();
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("fd00::1"))).isTrue();
        }

        @Test
        void multicast() throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("ff00::1"))).isTrue();
        }

        @Test
        void ipv4_mapped_loopback_is_blocked() throws Exception {
            // ::ffff:127.0.0.1 → 127.0.0.1 — must unmap and classify as loopback
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("::ffff:127.0.0.1"))).isTrue();
        }

        @Test
        void ipv4_mapped_private_is_blocked() throws Exception {
            // ::ffff:10.0.0.5 — the bypass form Java's isLoopbackAddress() misses
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("::ffff:10.0.0.5"))).isTrue();
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("::ffff:169.254.169.254"))).isTrue();
        }

        @Test
        void six_to_four_wraps_private_v4() throws Exception {
            // 2002::/16 wraps an embedded v4; 2002:0a00:0001:: contains 10.0.0.1
            byte[] raw = {
                    (byte) 0x20, (byte) 0x02,           // 2002::
                    (byte) 0x0A, (byte) 0x00,           // 10.0
                    (byte) 0x00, (byte) 0x01,           // .0.1
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            };
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByAddress(raw))).isTrue();
        }

        @Test
        void public_ipv6_is_allowed() throws Exception {
            assertThat(NetUtil.isPrivateOrLocal(InetAddress.getByName("2606:4700:4700::1111"))).isFalse(); // Cloudflare DNS
        }
    }

    @Nested
    @DisplayName("looksLikeIpLiteral — obfuscated-form detection")
    class IpLiteralDetection {

        @ParameterizedTest(name = "{0} → IP literal")
        @ValueSource(strings = {
                "127.0.0.1",
                "0.0.0.0",
                "2130706433",       // 127.0.0.1 in bare decimal
                "0x7f000001",       // 127.0.0.1 in hex
                "0x7f.0.0.1",       // hex with dots
                "0177.0.0.1",       // octal-style leading zero
                "127.1",            // short form
                "::1",
                "[::1]",
                "[::ffff:127.0.0.1]",
                "fe80::1",
        })
        void detected(String host) {
            assertThat(NetUtil.looksLikeIpLiteral(host))
                    .as("expected %s to be detected as IP literal", host)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0} → hostname")
        @ValueSource(strings = {
                "example.com",
                "api.partner.example.com",
                "xn--caf-dma.example.com",
                "localhost",       // technically a hostname, not a literal — InetAddress maps it
                "host-with-dashes.com",
        })
        void hostname_is_not_literal(String host) {
            assertThat(NetUtil.looksLikeIpLiteral(host))
                    .as("expected %s to be a hostname", host)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("normalizeHost — strips brackets, trailing dot, lowercases")
    class Normalize {

        @ParameterizedTest
        @CsvSource({
                "example.com,         example.com",
                "EXAMPLE.com,         example.com",
                "example.com.,        example.com",
                "[::1],               ::1",
                "[2001:db8::1],       2001:db8::1",
                "  example.com  ,     example.com",
        })
        void normalises(String input, String expected) {
            assertThat(NetUtil.normalizeHost(input)).isEqualTo(expected);
        }

        @Test
        void idn_to_ascii() {
            assertThat(NetUtil.normalizeHost("café.example.com")).isEqualTo("xn--caf-dma.example.com");
        }

        @Test
        void null_returns_null() {
            assertThat(NetUtil.normalizeHost(null)).isNull();
        }

        @Test
        void empty_returns_empty() {
            assertThat(NetUtil.normalizeHost("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Defense-in-depth: every obfuscated form is caught by at least one layer")
    class DefenseInDepth {

        // Two complementary defenses:
        //   1. URL-time `looksLikeIpLiteral` — refuses the request entirely
        //      when `rejectIpLiteralHosts=true` (the default).
        //   2. DNS-time `isPrivateOrLocal` — catches anything that DID get
        //      parsed as an IP (private/loopback/link-local/metadata).
        // Every bypass form below must be caught by at least one layer; if
        // both layers miss it, that's an SSRF.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "127.0.0.1",
                "0177.0.0.1",        // octal-style — Java 17+ may reject parse
                "127.1",             // short form
                "2130706433",        // bare decimal
                "0x7f000001",        // hex
                "0x7f.0.0.1",        // hex dotted
        })
        void at_least_one_layer_catches(String form) {
            boolean urlCheck = NetUtil.looksLikeIpLiteral(form);
            boolean dnsCheck = false;
            try {
                InetAddress addr = InetAddress.getByName(form);
                dnsCheck = NetUtil.isPrivateOrLocal(addr);
            } catch (UnknownHostException ignored) {
                // Newer JDKs refuse to parse these forms — counts as a third
                // defense (the request can't even start).
                dnsCheck = true;
            }
            assertThat(urlCheck || dnsCheck)
                    .as("expected %s to be blocked by URL check OR DNS check OR DNS parse refusal", form)
                    .isTrue();
        }
    }
}
