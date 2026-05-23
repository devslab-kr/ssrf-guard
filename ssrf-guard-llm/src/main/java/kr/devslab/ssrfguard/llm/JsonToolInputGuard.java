package kr.devslab.ssrfguard.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ToolInputGuard}. Treats the tool input as a JSON object,
 * walks the whole tree, finds every {@code http(s)://...} string at any
 * depth, and validates each through the supplied {@link UrlPolicy}.
 *
 * <h2>Why walk the whole tree</h2>
 * Naïve guards only inspect a top-level {@code "url"} field. Real-world
 * LLM tool schemas nest URLs in surprising places:
 *
 * <ul>
 *   <li>{@code {"request": {"target": "http://…"}}} — well-trained models
 *       generate nested context objects when the schema allows it.</li>
 *   <li>{@code {"urls": ["http://safe.com", "http://169.254.169.254/…"]}} —
 *       attacker hides one bad URL in a list of legitimate ones.</li>
 *   <li>Prompt-injected URLs that the model embeds inside a {@code reason}
 *       or {@code context} field while still passing a legit URL in
 *       {@code url}.</li>
 * </ul>
 *
 * Walking the whole tree means a single bad URL anywhere in the structure
 * trips the guard, not just one at a fixed location.
 *
 * <h2>Failure mode — error string vs. exception</h2>
 * Default is to return a structured JSON error string — the LLM sees it on
 * its next turn and can recover gracefully ("I can't fetch that URL").
 * Set {@code throwOnViolation = true} for CI / test contexts that want a
 * thrown {@link SsrfGuardException} instead.
 */
public final class JsonToolInputGuard implements ToolInputGuard {

    private static final Logger log = LoggerFactory.getLogger(JsonToolInputGuard.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UrlPolicy policy;
    private final boolean throwOnViolation;

    public JsonToolInputGuard(UrlPolicy policy) {
        this(policy, false);
    }

    public JsonToolInputGuard(UrlPolicy policy, boolean throwOnViolation) {
        this.policy = policy;
        this.throwOnViolation = throwOnViolation;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Concrete error-payload shape on rejection:
     *
     * <pre>{@code
     * {
     *   "error": "ssrf_blocked",
     *   "reason": "blocked_private_ip",
     *   "url": "http://169.254.169.254/...",
     *   "message": "DNS resolved to a private/loopback address: /169.254.169.254",
     *   "guidance": "Refuse the request or ask the user for a different URL. ..."
     * }
     * }</pre>
     *
     * <p>The {@code reason} field is the stable
     * {@link kr.devslab.ssrfguard.core.BlockReason#label() BlockReason label}
     * — safe to surface in metrics tags and grep against in log search.
     */
    @Override
    public String checkOrFormatError(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return null;

        JsonNode root;
        try {
            root = MAPPER.readTree(toolInput);
        } catch (Exception e) {
            // Not JSON — pass through. Many tools accept plain text or other
            // shapes; the HTTP-client-level guard catches anything that does
            // eventually become a URL.
            log.debug("ssrf-guard: tool input not parseable as JSON, skipping URL scan");
            return null;
        }

        List<String> urls = collectUrlLikeStrings(root);
        for (String url : urls) {
            URI uri;
            try {
                uri = new URI(url);
            } catch (URISyntaxException ignored) {
                continue;
            }
            String scheme = uri.getScheme();
            if (scheme == null) continue;
            // Only http/https. file://, gopher://, etc. would be rejected by
            // the URL policy anyway, but we don't want to false-positive on
            // strings like "mailto:..." or "urn:uuid:...".
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) continue;

            try {
                policy.validate(uri);
            } catch (SsrfGuardException e) {
                if (throwOnViolation) throw e;
                return formatErrorPayload(e, url);
            }
        }
        return null;
    }

    private static List<String> collectUrlLikeStrings(JsonNode node) {
        List<String> out = new ArrayList<>();
        collectUrlLikeStrings(node, out);
        return out;
    }

    private static void collectUrlLikeStrings(JsonNode node, List<String> out) {
        if (node == null) return;
        if (node.isTextual()) {
            String v = node.asText();
            if (looksLikeUrl(v)) out.add(v);
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) collectUrlLikeStrings(child, out);
            return;
        }
        if (node.isObject()) {
            // Jackson 2.18+ deprecated fields(); .properties() returns a
            // Set<Map.Entry<String,JsonNode>>. Iterate the values directly.
            for (JsonNode child : node.properties().stream()
                    .map(Map.Entry::getValue).toList()) {
                collectUrlLikeStrings(child, out);
            }
        }
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private String formatErrorPayload(SsrfGuardException e, String url) {
        try {
            // Typed record instead of Map.of(...) — see SsrfBlockPayload
            // javadoc for the GraalVM / wire-stability rationale.
            return MAPPER.writeValueAsString(
                    SsrfBlockPayload.of(e.reason().label(), url, e.getMessage()));
        } catch (Exception jsonErr) {
            // Fallback — minimal hand-rolled JSON. We control all the field
            // values here so this stays safe to concatenate.
            return "{\"error\":\"ssrf_blocked\",\"reason\":\"" + e.reason().label() + "\"}";
        }
    }

    public UrlPolicy policy() {
        return policy;
    }

    public boolean throwOnViolation() {
        return throwOnViolation;
    }
}
