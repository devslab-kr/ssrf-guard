package kr.devslab.ssrfguard.core;

/**
 * Why a request was rejected by SSRF Guard. The label is what surfaces in
 * metrics ({@code ssrf_guard_blocked_total{reason=...}}) and structured logs,
 * so values are kept short and stable across versions.
 */
public enum BlockReason {

    /** URI scheme not in the allow-list (e.g. {@code file://}, {@code gopher://}). */
    BLOCKED_SCHEME("blocked_scheme"),

    /** Host not in the whitelist of exact hosts / suffixes. */
    BLOCKED_HOST("blocked_host"),

    /** TCP port not in the allow-list. */
    BLOCKED_PORT("blocked_port"),

    /** Host parsed as an IP literal but {@code rejectIpLiteralHosts} was on. */
    BLOCKED_IP_LITERAL("blocked_ip_literal"),

    /** URL contained userinfo ({@code user:pass@}). */
    BLOCKED_USERINFO("blocked_userinfo"),

    /** DNS resolved to a private/loopback/link-local/metadata address. */
    BLOCKED_PRIVATE_IP("blocked_private_ip"),

    /** Redirect target failed any of the above checks. */
    BLOCKED_REDIRECT("blocked_redirect"),

    /** Catch-all for client-specific failure modes (e.g. unresolvable DNS). */
    BLOCKED_OTHER("blocked_other");

    private final String label;

    BlockReason(String label) {
        this.label = label;
    }

    /** Stable string label safe to ship as a metric tag. */
    public String label() {
        return label;
    }
}
