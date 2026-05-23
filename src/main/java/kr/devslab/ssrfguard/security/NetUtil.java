package kr.devslab.ssrfguard.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Small bag of host-string and IP-classification helpers used by every guard
 * component. Package-private on purpose — these aren't part of the public API.
 */
public final class NetUtil {
    private NetUtil() {}

    /**
     * True for any address consumers are usually trying to keep their service
     * from reaching: loopback ({@code 127.0.0.0/8}, {@code ::1}), link-local
     * ({@code 169.254.0.0/16}, {@code fe80::/10}), site-local /
     * RFC-1918 private ranges, multicast, the cloud-metadata
     * carrier-grade NAT range ({@code 100.64.0.0/10}), the benchmark range
     * ({@code 198.18.0.0/15}), and the IPv6 unique-local block
     * ({@code fc00::/7}). The list is hand-rolled rather than relying solely
     * on {@code isSiteLocalAddress()} because the JDK helper misses several
     * categories an SSRF attacker would want.
     */
    public static boolean isPrivateOrLocal(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) return true;
        if (addr.isMulticastAddress()) return true;
        if (addr.isSiteLocalAddress()) return true;

        if (addr instanceof Inet4Address a4) {
            byte[] b = a4.getAddress();
            int i = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
            if (inRange(i, 0x00000000, 8)) return true;  // 0.0.0.0/8
            if (inRange(i, 0x64400000, 10)) return true; // 100.64.0.0/10 (CGNAT)
            if (inRange(i, 0x7F000000, 8)) return true;  // 127.0.0.0/8
            if (inRange(i, 0xA9FE0000, 16)) return true; // 169.254.0.0/16
            if (inRange(i, 0xC6120000, 15)) return true; // 198.18.0.0/15 (benchmark)
            return i == 0xFFFFFFFF;                      // 255.255.255.255
        } else if (addr instanceof Inet6Address a6) {
            byte[] b = a6.getAddress();
            if (isAllZeroExceptLast(b) && (b[b.length - 1] == 0x01)) return true;      // ::1
            if ((b[0] & (byte) 0xFE) == (byte) 0xFC) return true;                       // fc00::/7 ULA
            if ((b[0] == (byte) 0xFE) && ((b[1] & (byte) 0xC0) == (byte) 0x80)) return true; // fe80::/10 link-local
            return (b[0] & 0xFF) == 0xFF;                                               // ff00::/8 multicast
        }
        return false;
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
     */
    public static String normalizeHost(String host) {
        return java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED);
    }

    /**
     * True iff {@code host} is in {@code exactHosts} (case-insensitive, IDN-
     * normalised), or {@code host} ends with a label of one of {@code suffixes}.
     * "Ends with a label" means the suffix matches either the whole host or
     * is preceded by a dot — {@code partner.example.com} is in
     * {@code example.com} but {@code badexample.com} is not.
     */
    public static boolean hostMatches(String host, Iterable<String> exactHosts, Iterable<String> suffixes) {
        String h = normalizeHost(host).toLowerCase();
        for (String e : exactHosts)
            if (h.equals(normalizeHost(e).toLowerCase())) return true;
        for (String s : suffixes) {
            String suf = normalizeHost(s).toLowerCase();
            if (h.equals(suf) || h.endsWith("." + suf)) return true;
        }
        return false;
    }
}
