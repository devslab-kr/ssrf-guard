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
            // No scheme filtering here, deliberately. Collection is generous
            // and the POLICY decides — a filter at the collection stage is
            // what let uppercase-scheme URLs skip validation entirely in
            // 3.1.1, and the same shape previously let `file://`, `ftp://`
            // and `gopher://` through untouched. `mailto:` and `urn:` never
            // reach here because collection requires an authority (`://`).
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
            if (looksLikeUrl(v)) {
                out.add(v.trim());
            } else if (looksProtocolRelative(v)) {
                // Validate the authority as if the URL resolves to https.
                out.add("https:" + v.trim());
            }
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

    // An embedded URL of ANY scheme can start anywhere in the string — even
    // glued to preceding text ("seehttp://evil.example"), which fails closed
    // as an unknown scheme rather than slipping past — and runs to the first
    // whitespace/quote/angle-bracket. Prose punctuation stuck to the tail
    // ("….com/docs.", "(…)") is trimmed afterwards by trimEmbeddedTail.
    private static final Pattern EMBEDDED_SCHEME_URL =
            Pattern.compile("[a-z][a-z0-9+.-]*://[^\\s\"'`<>]+", Pattern.CASE_INSENSITIVE);

    // Embedded protocol-relative `//authority`, only at the start of the
    // string or after whitespace/quote/bracket — so the `//` inside
    // `scheme://` or a URL path never matches — with the same
    // host-looking authority requirement as the whole-string variant.
    private static final Pattern EMBEDDED_PROTOCOL_RELATIVE = Pattern.compile(
            "(?<=^|[\\s\"'`<(\\[{])//(?:\\[[^\\s\\]]+\\]|[^\\s/?#\"'`<>]*[.:][^\\s/?#\"'`<>]*|localhost)(?:[/?#][^\\s\"'`<>]*)?",
            Pattern.CASE_INSENSITIVE);

    private static void collectEmbedded(String value, List<String> out) {
        Matcher m = EMBEDDED_SCHEME_URL.matcher(value);
        while (m.find()) {
            out.add(trimEmbeddedTail(m.group()));
        }
        Matcher rel = EMBEDDED_PROTOCOL_RELATIVE.matcher(value);
        while (rel.find()) {
            out.add("https:" + trimEmbeddedTail(rel.group()));
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

    /**
     * Any scheme followed by an authority ({@code scheme://}). Schemes with
     * no authority ({@code mailto:}, {@code urn:}, {@code data:}) are not
     * URL-fetch surfaces and stay ignored. Case-insensitive: URI schemes are
     * case-insensitive per RFC 3986 §3.1, and a case-sensitive test here is
     * what let uppercase-scheme URLs bypass the policy before 3.1.1.
     */
    private static final Pattern SCHEME_URL =
            Pattern.compile("^[a-z][a-z0-9+.-]*://", Pattern.CASE_INSENSITIVE);

    /**
     * Protocol-relative {@code //authority} — collected only when the
     * authority looks like a host (contains a dot or a colon, or is
     * {@code localhost}), so a bare {@code // comment} stays ignored.
     */
    private static final Pattern PROTOCOL_RELATIVE = Pattern.compile(
            "^//(?:\\[[^\\]]+\\]|[^\\s/?#]*[.:][^\\s/?#]*|localhost)(?:[/?#]|$)",
            Pattern.CASE_INSENSITIVE);

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        return SCHEME_URL.matcher(s.trim()).find();
    }

    private static boolean looksProtocolRelative(String s) {
        if (s == null) return false;
        return PROTOCOL_RELATIVE.matcher(s.trim()).find();
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
