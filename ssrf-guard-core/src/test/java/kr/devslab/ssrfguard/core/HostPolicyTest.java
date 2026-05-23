package kr.devslab.ssrfguard.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HostPolicyTest {

    @Test
    void normalises_at_construction() {
        HostPolicy p = new HostPolicy(List.of("API.PARTNER.COM", "café.example.com"), List.of("EXAMPLE.com."));
        assertThat(p.exactHosts()).containsExactlyInAnyOrder("api.partner.com", "xn--caf-dma.example.com");
        assertThat(p.suffixes()).containsExactly("example.com");
    }

    @Test
    void null_entries_are_ignored() {
        HostPolicy p = new HostPolicy(java.util.Arrays.asList("a.com", null, ""), java.util.Arrays.asList(null, "b.com"));
        assertThat(p.exactHosts()).containsExactly("a.com");
        assertThat(p.suffixes()).containsExactly("b.com");
    }

    @Test
    void empty_factory() {
        HostPolicy p = HostPolicy.empty();
        assertThat(p.exactHosts()).isEmpty();
        assertThat(p.suffixes()).isEmpty();
        assertThat(p.allows("anything.com")).isFalse();
    }

    @Test
    void allows_via_exact() {
        HostPolicy p = new HostPolicy(List.of("api.example.com"), List.of());
        assertThat(p.allows("api.example.com")).isTrue();
        assertThat(p.allows("v2.api.example.com")).isFalse();
    }

    @Test
    void allows_via_suffix_subdomain() {
        HostPolicy p = new HostPolicy(List.of(), List.of("example.com"));
        assertThat(p.allows("example.com")).isTrue();
        assertThat(p.allows("api.example.com")).isTrue();
        assertThat(p.allows("badexample.com")).isFalse();
    }
}
