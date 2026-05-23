package kr.devslab.ssrfguard.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Immutable whitelist of hosts. The two list flavours have different
 * semantics:
 * <ul>
 *   <li>{@link #exactHosts()} — case-insensitive, IDN-normalised string
 *       equality. {@code api.partner.com} matches {@code api.partner.com}
 *       and {@code API.partner.com} but not {@code v2.api.partner.com}.</li>
 *   <li>{@link #suffixes()} — same comparison but also accepts any
 *       subdomain. {@code partner.com} matches itself, {@code api.partner.com},
 *       {@code v2.api.partner.com}, … but <i>not</i> {@code attacker.partner.com.evil}.</li>
 * </ul>
 *
 * <p>Construction normalises both lists once so the per-request match path
 * is just two string-equals/endsWith comparisons.
 */
public final class HostPolicy {

    private final List<String> exactHosts;
    private final List<String> suffixes;

    public HostPolicy(Collection<String> exactHosts, Collection<String> suffixes) {
        this.exactHosts = freezeNormalised(exactHosts);
        this.suffixes = freezeNormalised(suffixes);
    }

    public List<String> exactHosts() {
        return exactHosts;
    }

    public List<String> suffixes() {
        return suffixes;
    }

    /** Convenience constructor for empty-whitelist policies (used by tests). */
    public static HostPolicy empty() {
        return new HostPolicy(Collections.emptyList(), Collections.emptyList());
    }

    /** True iff {@code host} matches at least one exact host or suffix. */
    public boolean allows(String host) {
        return NetUtil.hostMatches(host, exactHosts, suffixes);
    }

    private static List<String> freezeNormalised(Collection<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            if (s == null) continue;
            String norm = NetUtil.normalizeHost(s);
            if (norm != null && !norm.isEmpty()) out.add(norm);
        }
        return List.copyOf(out);
    }
}
