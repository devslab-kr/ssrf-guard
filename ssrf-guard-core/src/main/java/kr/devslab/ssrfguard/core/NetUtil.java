package kr.devslab.ssrfguard.core;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Host-string and IP-classification helpers. Used by every guard component —
 * deliberately stateless / static so wrappers in non-Spring modules can
 * lean on it without dragging in a {@code @Component}.
 */
public final class NetUtil {
    private NetUtil() {}

    /**
     * True for any address consumers are usually trying to keep their service
     * from reaching:
     * <ul>
     *   <li>loopback ({@code 127.0.0.0/8}, {@code ::1})</li>
     *   <li>link-local ({@code 169.254.0.0/16} — incl. AWS metadata {@code 169.254.169.254}; {@code fe80::/10})</li>
     *   <li>RFC-1918 site-local + the {@code 0.0.0.0/8} wildcard range</li>
     *   <li>multicast and broadcast</li>
     *   <li>carrier-grade NAT ({@code 100.64.0.0/10}) and benchmark ({@code 198.18.0.0/15})</li>
     *   <li>IPv6 unique-local ({@code fc00::/7})</li>
     *   <li>IPv4-mapped IPv6 — both the legacy form ({@code ::ffff:x.x.x.x})
     *       and the 6to4 wrapper ({@code 2002::/16}) — get unmapped and the
     *       embedded v4 address is re-classified, so {@code [::ffff:127.0.0.1]}
     *       is treated as loopback, not a "public" v6 address.</li>
     * </ul>
     * The list is hand-rolled rather than relying solely on
     * {@code isSiteLocalAddress()} because the JDK helper misses several
     * categories an SSRF attacker would want.
     */
    public static boolean isPrivateOrLocal(InetAddress addr) {
        if (addr == null) return false;
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) return true;
        if (addr.isMulticastAddress()) return true;
        if (addr.isSiteLocalAddress()) return true;

        if (addr instanceof Inet4Address a4) {
            return isPrivateIpv4(a4.getAddress());
        } else if (addr instanceof Inet6Address a6) {
            byte[] b = a6.getAddress();
            // IPv4-mapped IPv6 (::ffff:x.x.x.x): bytes 0-9 are zero, 10-11 are 0xFF.
            // Java's isLoopbackAddress() catches ::ffff:127.0.0.1 by accident, but
            // not, say, ::ffff:10.0.0.5 (private RFC-1918 mapped). Unmap and recheck.
            if (isIpv4MappedV6(b)) {
                byte[] v4 = new byte[]{ b[12], b[13], b[14], b[15] };
                return isPrivateIpv4(v4);
            }
            // 6to4 prefix (2002::/16) embeds an IPv4 address in bytes 2-5. Same
            // bypass risk: 2002:0a00::/24 wraps 10.0.0.0/8.
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
                byte[] v4 = new byte[]{ b[2], b[3], b[4], b[5] };
                return isPrivateIpv4(v4);
            }
            if (isAllZeroExceptLast(b) && (b[b.length - 1] == 0x01)) return true;          // ::1
            if ((b[0] & (byte) 0xFE) == (byte) 0xFC) return true;                          // fc00::/7 ULA
            if ((b[0] == (byte) 0xFE) && ((b[1] & (byte) 0xC0) == (byte) 0x80)) return true; // fe80::/10 link-local
            return (b[0] & 0xFF) == 0xFF;                                                  // ff00::/8 multicast
        }
        return false;
    }

    private static boolean isIpv4MappedV6(byte[] b) {
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }

    private static boolean isPrivateIpv4(byte[] b) {
        int i = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if (inRange(i, 0x00000000, 8)) return true;  // 0.0.0.0/8
        if (inRange(i, 0x0A000000, 8)) return true;  // 10.0.0.0/8 (RFC 1918)
        if (inRange(i, 0x64400000, 10)) return true; // 100.64.0.0/10 (CGNAT)
        if (inRange(i, 0x7F000000, 8)) return true;  // 127.0.0.0/8 loopback
        if (inRange(i, 0xA9FE0000, 16)) return true; // 169.254.0.0/16 link-local
        if (inRange(i, 0xAC100000, 12)) return true; // 172.16.0.0/12 (RFC 1918)
        if (inRange(i, 0xC0A80000, 16)) return true; // 192.168.0.0/16 (RFC 1918)
        if (inRange(i, 0xC6120000, 15)) return true; // 198.18.0.0/15 (benchmark)
        return i == 0xFFFFFFFF;                      // 255.255.255.255 broadcast
    }

    private static boolean inRange(int ip, int base, int prefixLen) {
        int mask = (prefixLen == 0) ? 0 : ~((1 << (32 - prefixLen)) - 1);
        return (ip & mask) == (base & mask);
    }

    private static boolean isAllZeroExceptLast(byte[] b) {
        for (int i = 0; i < b.length - 1; i++) if (b[i] != 0) return false;
        return true;
    }

    /**
     * Normalises a host to its ASCII (Punycode) form so internationalised
     * hostnames (IDN) compare correctly against the configured whitelist.
     * Lowercase is applied by {@link #hostMatches}.
     *
     * <p>Trims a trailing dot ({@code example.com.} → {@code example.com}) so
     * an attacker can't sneak the FQDN absolute form past a string-equality
     * whitelist.
     */
    public static String normalizeHost(String host) {
        if (host == null) return null;
        String h = host.trim();
        if (h.isEmpty()) return h;
        // Strip IPv6 brackets so callers comparing host strings see the same
        // form they configured (config rarely includes brackets).
        if (h.startsWith("[") && h.endsWith("]") && h.length() > 2) {
            h = h.substring(1, h.length() - 1);
        }
        // Trailing dot is the absolute-FQDN marker. Equivalent to the bare
        // form for DNS but unequal for plain string comparison — strip it.
        if (h.endsWith(".") && h.length() > 1) {
            h = h.substring(0, h.length() - 1);
        }
        // IDN.toASCII rejects IP literals on some JDKs; the looksLikeIpLiteral
        // check above keeps IDN out of the path for those.
        if (looksLikeIpLiteral(h)) {
            return h.toLowerCase();
        }
        return IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).toLowerCase();
    }

    /**
     * True iff {@code host} is in {@code exactHosts} (case-insensitive, IDN-
     * normalised), or {@code host} ends with a label of one of {@code suffixes}.
     * "Ends with a label" means the suffix matches either the whole host or
     * is preceded by a dot — {@code partner.example.com} is in
     * {@code example.com} but {@code badexample.com} is not.
     */
    public static boolean hostMatches(String host, Iterable<String> exactHosts, Iterable<String> suffixes) {
        if (host == null) return false;
        String h = normalizeHost(host);
        for (String e : exactHosts) {
            if (h.equals(normalizeHost(e))) return true;
        }
        for (String s : suffixes) {
            String suf = normalizeHost(s);
            if (h.equals(suf) || h.endsWith("." + suf)) return true;
        }
        return false;
    }

    /**
     * True if {@code host} appears to be an IP literal in any common form
     * — dotted decimal ({@code 127.0.0.1}), bare decimal ({@code 2130706433}),
     * hex ({@code 0x7f000001}), partially-shortened ({@code 127.1}), or
     * IPv6 (contains {@code :}).
     *
     * <p><b>Why this matters.</b> Java's {@code URI.getHost()} returns the
     * host as-is — it doesn't know whether {@code 2130706433} is "decimal
     * IP literal" or "weird hostname." {@code InetAddress.getByName()} then
     * parses several obfuscated forms as IPs (Java has historically been
     * lenient here), which means a whitelist of {@code example.com} that
     * doesn't reject IP-literal forms upfront can be bypassed via
     * {@code http://2130706433/} → {@code 127.0.0.1}.
     *
     * <p>Most production whitelists are <i>domain names</i>; allowing any
     * IP literal at all is rare. The {@code rejectIpLiteralHosts} policy
     * (default on) uses this method to refuse the entire class.
     */
    public static boolean looksLikeIpLiteral(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            // Bracketed → must be IPv6.
            return true;
        }
        if (h.contains(":")) {
            // Bare colon outside brackets is unambiguous IPv6.
            return true;
        }
        // Pure decimal — 4294967295 max for IPv4 in bare form. Hostnames
        // with all-digit labels are technically legal (and used internally
        // by some test harnesses), so this triggers only when no dot is
        // present OR every label is numeric.
        if (h.matches("^\\d+$")) return true;
        // Hex literal: 0x7f.0.0.1, 0x7f000001
        if (h.matches("^0[xX][0-9a-fA-F.]+$")) return true;
        // Octal-style leading-zero (0177.0.0.1) — InetAddress on older JDKs
        // parses this as octal.
        if (h.matches("^0[0-9.]+$")) return true;
        // Standard dotted IPv4
        if (h.matches("^\\d{1,3}(\\.\\d{1,3}){1,3}$")) return true;
        return false;
    }
}
