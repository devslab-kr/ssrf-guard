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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * <h2>Embedded URL scanning ({@code scanEmbedded})</h2>
 * By default only strings whose whole (trimmed) value is an
 * {@code http(s)://} URL are collected. A URL buried mid-sentence
 * ("summarize http://169.254.169.254/ please") — the shape a
 * prompt-injected instruction typically takes — is not. Opt in with
 * {@code scanEmbedded = true} to also extract and validate URLs embedded
 * anywhere inside argument strings. Strictly additive: everything the
 * base scanner flags stays flagged. Deliberately aggressive: URL-shaped
 * text inside prose or code snippets is validated against the policy, so
 * non-allowlisted hosts there count as violations.
 */
public final class JsonToolInputGuard implements ToolInputGuard {

    private static final Logger log = LoggerFactory.getLogger(JsonToolInputGuard.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UrlPolicy policy;
    private final boolean throwOnViolation;
    private final boolean scanEmbedded;

    public JsonToolInputGuard(UrlPolicy policy) {
        this(policy, false);
    }

    public JsonToolInputGuard(UrlPolicy policy, boolean throwOnViolation) {
        this(policy, throwOnViolation, false);
    }

    /**
     * @param scanEmbedded when {@code true}, also scan for {@code http(s)://}
     *                     URLs embedded mid-sentence inside argument strings
     *                     (see class javadoc). Default: {@code false}.
     */
    public JsonToolInputGuard(UrlPolicy policy, boolean throwOnViolation, boolean scanEmbedded) {
        this.policy = policy;
        this.throwOnViolation = throwOnViolation;
        this.scanEmbedded = scanEmbedded;
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

        List<String> urls = collectUrlLikeStrings(root, scanEmbedded);
        for (String url : urls) {
            URI uri = parseForValidation(url);
            if (uri == null) continue;
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

    /**
     * {@code java.net.URI} rejects characters browsers and HTTP clients
     * happily send unencoded ({@code [}, {@code {}, ...). A parse failure
     * must not let the URL skip validation: the policy only judges
     * {@code scheme://authority}, so retry with everything after the
     * authority dropped. Returns {@code null} only when even the authority
     * is unparseable — {@link UrlPolicy} rejects null/empty hosts anyway,
     * so nothing resolvable slips through.
     */
    private static URI parseForValidation(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException firstFailure) {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return null;
            int authorityEnd = url.length();
            for (int i = schemeEnd + 3; i < url.length(); i++) {
                char c = url.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    authorityEnd = i;
                    break;
                }
            }
            try {
                return new URI(url.substring(0, authorityEnd));
            } catch (URISyntaxException ignored) {
                return null;
            }
        }
    }

    private static List<String> collectUrlLikeStrings(JsonNode node, boolean scanEmbedded) {
        List<String> out = new ArrayList<>();
        collectUrlLikeStrings(node, out, scanEmbedded);
        return out;
    }

    private static void collectUrlLikeStrings(JsonNode node, List<String> out, boolean scanEmbedded) {
        if (node == null) return;
        if (node.isTextual()) {
            String v = node.asText();
            // Trimmed — looksLikeUrl() tolerates surrounding whitespace, and
            // an untrimmed " http://…" would fail URI parsing and skip
            // validation entirely.
            if (looksLikeUrl(v)) out.add(v.trim());
            if (scanEmbedded) collectEmbedded(v, out);
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) collectUrlLikeStrings(child, out, scanEmbedded);
            return;
        }
        if (node.isObject()) {
            // Jackson 2.18+ deprecated fields(); .properties() returns a
            // Set<Map.Entry<String,JsonNode>>. Iterate the values directly.
            for (JsonNode child : node.properties().stream()
                    .map(Map.Entry::getValue).toList()) {
                collectUrlLikeStrings(child, out, scanEmbedded);
            }
        }
    }

    // An embedded http(s) URL can start anywhere in the string — even glued
    // to preceding text ("seehttp://evil.example") — and runs to the first
    // whitespace/quote/angle-bracket. Prose punctuation stuck to the tail
    // ("….com/docs.", "(…)") is trimmed afterwards by trimEmbeddedTail.
    private static final Pattern EMBEDDED_HTTP_URL =
            Pattern.compile("https?://[^\\s\"'`<>]+", Pattern.CASE_INSENSITIVE);

    private static void collectEmbedded(String value, List<String> out) {
        Matcher m = EMBEDDED_HTTP_URL.matcher(value);
        while (m.find()) {
            out.add(trimEmbeddedTail(m.group()));
        }
    }

    private static final String TRAILING_PROSE_PUNCTUATION = ".,;:!?'\"`";

    // "see https://evil.example/x)." — the sentence's punctuation is not
    // part of the URL. Closing brackets are only trimmed when unbalanced, so
    // "https://en.example.org/wiki/Foo_(bar)" survives intact. Host-level
    // policy decisions are unaffected either way — only the path can lose
    // characters here.
    private static String trimEmbeddedTail(String candidate) {
        String out = candidate;
        while (!out.isEmpty()) {
            char last = out.charAt(out.length() - 1);
            if (TRAILING_PROSE_PUNCTUATION.indexOf(last) >= 0) {
                out = out.substring(0, out.length() - 1);
                continue;
            }
            char opener = switch (last) {
                case ')' -> '(';
                case ']' -> '[';
                case '}' -> '{';
                default -> '\0';
            };
            if (opener != '\0' && countChar(out, opener) < countChar(out, last)) {
                out = out.substring(0, out.length() - 1);
                continue;
            }
            break;
        }
        return out;
    }

    private static int countChar(String value, char c) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == c) n++;
        }
        return n;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        // Lower-case before the prefix test: URI schemes are case-insensitive
        // (RFC 3986 §3.1), so HTTP:// / HtTpS:// must be detected too. The
        // downstream scheme check already uses equalsIgnoreCase; a
        // case-sensitive match here would let an uppercase-scheme URL skip
        // collection entirely and bypass the policy.
        String trimmed = s.trim().toLowerCase();
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

    public boolean scanEmbedded() {
        return scanEmbedded;
    }
}
