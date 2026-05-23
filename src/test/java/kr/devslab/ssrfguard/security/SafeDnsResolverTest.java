package kr.devslab.ssrfguard.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SafeDnsResolver}. We can't easily mock
 * {@code InetAddress.getAllByName}, but we don't need to — the resolver's
 * decision points (whitelist match, private-IP filter, "nothing left after
 * filter") can be exercised against real (publicly routable) hostnames and
 * against the loopback that the JDK is guaranteed to resolve locally.
 */
class SafeDnsResolverTest {

    @Test
    void resolve_rejectsHostNotInWhitelist() {
        SafeDnsResolver resolver = new SafeDnsResolver(
                List.of("example.com"), List.of(), true);

        assertThatThrownBy(() -> resolver.resolve("evil.com"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("Host not in whitelist");
    }

    @Test
    void resolve_rejectsLoopbackWhenBlockingEnabled() {
        // localhost is whitelisted but its address is loopback ⇒ everything
        // gets filtered, the resolver throws "No allowed IP after filtering".
        SafeDnsResolver resolver = new SafeDnsResolver(
                List.of("localhost"), List.of(), true);

        assertThatThrownBy(() -> resolver.resolve("localhost"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("No allowed IP after filtering");
    }

    @Test
    void resolve_returnsLoopbackWhenBlockingDisabled() throws UnknownHostException {
        // Same whitelist; blockPrivate=false ⇒ loopback comes through.
        SafeDnsResolver resolver = new SafeDnsResolver(
                List.of("localhost"), List.of(), false);

        InetAddress[] resolved = resolver.resolve("localhost");
        assertThat(resolved).isNotEmpty();
    }

    @Test
    void resolve_suffixWhitelistAllowsSubdomain() throws UnknownHostException {
        // Using `iana.org` because it's a stable, public host. The resolver
        // would block private/loopback IPs but `iana.org` is publicly routable.
        SafeDnsResolver resolver = new SafeDnsResolver(
                List.of(), List.of("iana.org"), true);

        // Whitelist match works — actual DNS resolution may or may not succeed
        // in CI depending on outbound network access. Assert the whitelist
        // gate is what we control; if DNS fails we surface that separately.
        try {
            InetAddress[] resolved = resolver.resolve("www.iana.org");
            assertThat(resolved).isNotEmpty();
        } catch (UnknownHostException e) {
            // No outbound DNS in this CI environment — make sure the failure
            // wasn't the whitelist gate.
            assertThat(e.getMessage()).doesNotContain("Host not in whitelist");
        }
    }
}
