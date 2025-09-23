package devs.lab.ssrf.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

final class NetUtil {
    private NetUtil() {}

    static boolean isPrivateOrLocal(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) return true;
        if (addr.isMulticastAddress()) return true;
        if (addr.isSiteLocalAddress()) return true;

        if (addr instanceof Inet4Address a4) {
            byte[] b = a4.getAddress();
            int i = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
            if (inRange(i, 0x00000000, 8)) return true;  // 0.0.0.0/8
            if (inRange(i, 0x64400000, 10)) return true; // 100.64.0.0/10
            if (inRange(i, 0x7F000000, 8)) return true;  // 127.0.0.0/8
            if (inRange(i, 0xA9FE0000, 16)) return true; // 169.254.0.0/16
            if (inRange(i, 0xC6120000, 15)) return true; // 198.18.0.0/15
            return i == 0xFFFFFFFF;            // 255.255.255.255
        } else if (addr instanceof Inet6Address a6) {
            byte[] b = a6.getAddress();
            if (isAllZeroExceptLast(b) && (b[b.length - 1] == 0x01)) return true;      // ::1
            if ((b[0] & (byte)0xFE) == (byte)0xFC) return true;                        // fc00::/7 ULA
            if ((b[0] == (byte)0xFE) && ((b[1] & (byte)0xC0) == (byte)0x80)) return true; // fe80::/10 link-local
            return (b[0] & 0xFF) == 0xFF;                                     // ff00::/8 multicast
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

    static String normalizeHost(String host) {
        return java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED);
    }

    static boolean hostMatches(String host, Iterable<String> exactHosts, Iterable<String> suffixes) {
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