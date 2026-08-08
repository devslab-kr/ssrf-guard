package kr.devslab.ssrfguard.core;

import java.net.URI;
import java.util.Locale;

/**
 * The one definition of what a redirect hop must pass, shared by every
 * adapter.
 *
 * <h2>Why this lives in core</h2>
 * The JS sibling ({@code @devslab/ssrf-guard-js}) owns its own redirect
 * loop, so "what do we check on a hop" is written once. On the JVM each
 * adapter wraps someone else's client, and the client owns the loop — so
 * the loop cannot move here. The <i>decision</i> can, and must: before this
 * class existed each adapter improvised, and they disagreed.
 *
 * <table>
 *   <caption>What each adapter re-checked per hop before this class</caption>
 *   <tr><th>Adapter</th><th>Re-checked</th></tr>
 *   <tr><td>httpclient5</td><td>scheme + DNS only — not port, userinfo or IP-literal</td></tr>
 *   <tr><td>okhttp</td><td>host allowlist + private IP (via the {@code Dns} layer) only</td></tr>
 *   <tr><td>jdkhttp</td><td>nothing — the JDK client followed redirects internally</td></tr>
 * </table>
 *
 * A redirect is the whole point of an SSRF guard: the attacker controls an
 * allowlisted host's response and points it somewhere else. A hop that gets
 * a weaker check than the first request is a hole with extra steps.
 *
 * <h2>What a hop must pass</h2>
 * The full {@link UrlPolicy} — the same checks the first request gets.
 * Failures are re-thrown as {@link BlockReason#BLOCKED_REDIRECT} so callers
 * and metrics can tell "the request you made was refused" from "something
 * tried to bounce you elsewhere", with the original rule named in the
 * message.
 */
public final class RedirectGuard {

    private RedirectGuard() {
    }

    /**
     * Re-validate one redirect target against the full policy.
     *
     * @param policy   the same policy the first request was validated with
     * @param location the resolved absolute redirect target
     * @return {@code location}, so call sites can inline this
     * @throws SsrfGuardException with {@link BlockReason#BLOCKED_REDIRECT}
     */
    public static URI validateHop(UrlPolicy policy, URI location) {
        if (location == null) {
            throw new SsrfGuardException(BlockReason.BLOCKED_REDIRECT, null, null,
                    "Blocked redirect: no location");
        }
        try {
            policy.validate(location);
        } catch (SsrfGuardException e) {
            throw new SsrfGuardException(BlockReason.BLOCKED_REDIRECT, e.scheme(), e.host(),
                    "Blocked redirect: " + e.getMessage());
        }
        return location;
    }

    /**
     * Whether a redirect from {@code from} to {@code to} crosses an origin,
     * in which case credentials must not be replayed. Compares scheme, host
     * and effective port — a scheme change alone moves the origin, and so
     * does {@code https://h/} to {@code https://h:8443/}.
     */
    public static boolean crossOrigin(URI from, URI to) {
        if (from == null || to == null) return true;
        return !equalsIgnoreCaseNullSafe(from.getScheme(), to.getScheme())
                || !equalsIgnoreCaseNullSafe(from.getHost(), to.getHost())
                || effectivePort(from) != effectivePort(to);
    }

    /**
     * Per the fetch specification's redirect handling, which the JS sibling
     * follows: {@code 303} always becomes {@code GET}, and {@code 301}/
     * {@code 302} downgrade a {@code POST}. The body must not be replayed
     * in either case.
     */
    public static boolean downgradesToGet(int status, String method) {
        String m = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        if (status == 303) return !m.equals("GET") && !m.equals("HEAD");
        return (status == 301 || status == 302) && m.equals("POST");
    }

    /** Headers that must be dropped when a redirect changes origin. */
    public static boolean isCredentialHeader(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("authorization") || n.equals("proxy-authorization") || n.equals("cookie");
    }

    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) return port;
        String scheme = uri.getScheme();
        if (scheme == null) return -1;
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            default -> -1;
        };
    }

    private static boolean equalsIgnoreCaseNullSafe(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
