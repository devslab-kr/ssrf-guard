package kr.devslab.ssrfguard.springai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps a Spring AI {@link ToolCallback} so URL-shaped tool inputs are
 * validated against an {@link UrlPolicy} before the underlying tool executes.
 *
 * <p>The threat this closes: an LLM agent's "fetch_url" / "scrape_page" /
 * "summarize_link" tool. The model accepts a URL from the user (sometimes
 * directly, sometimes indirectly through RAG search results), passes it as
 * a tool argument, and your code does {@code restClient.get().uri(url)…}.
 * That's a one-line SSRF vector if the URL isn't validated — and the SSRF
 * Guard interceptors at the HTTP-client layer DO catch it. But the wrap
 * here gives the LLM a structured error <i>at tool-call time</i>, before
 * any HTTP traffic is generated, so the model can recover gracefully
 * ("I'm not allowed to fetch that URL, let me ask the user for a
 * different one") instead of seeing an HTTP exception it can't reason
 * about.
 *
 * <h2>Heuristic URL detection</h2>
 * The tool input arrives as a JSON string ({@code {"url":"http://...","timeout":5}}).
 * We walk the JSON tree and, for every string-valued leaf, try to parse it
 * as a URI. Anything that parses AND has an http/https scheme runs through
 * the policy. False positives (a string field that happens to start with
 * {@code "http://..."} but isn't meant as a URL) are rare and would be
 * blocked anyway if they'd be used as URLs downstream.
 *
 * <h2>Failure mode — error string vs. exception</h2>
 * We return a structured JSON error string rather than throwing.
 * Spring AI surfaces tool exceptions as failed tool calls and the LLM has
 * to interpret them; returning an error string keeps the conversation
 * flowing and is what most production agents want. Consumers who'd rather
 * fail loud can pass {@code throwOnViolation = true} to the constructor.
 */
public final class SsrfGuardedToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuardedToolCallback.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolCallback delegate;
    private final UrlPolicy policy;
    private final boolean throwOnViolation;

    public SsrfGuardedToolCallback(ToolCallback delegate, UrlPolicy policy) {
        this(delegate, policy, false);
    }

    public SsrfGuardedToolCallback(ToolCallback delegate, UrlPolicy policy, boolean throwOnViolation) {
        this.delegate = delegate;
        this.policy = policy;
        this.throwOnViolation = throwOnViolation;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String violation = checkUrlsInJson(toolInput);
        if (violation != null) {
            return violation;
        }
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String violation = checkUrlsInJson(toolInput);
        if (violation != null) {
            return violation;
        }
        return delegate.call(toolInput, toolContext);
    }

    /**
     * Walks {@code toolInput} as JSON, finds every URL-looking string, and
     * runs each through {@link UrlPolicy}. Returns {@code null} if every URL
     * passes; otherwise returns a JSON error string the LLM can read and
     * react to. If {@code throwOnViolation = true}, re-throws the
     * SsrfGuardException instead.
     */
    private String checkUrlsInJson(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return null;
        JsonNode root;
        try {
            root = MAPPER.readTree(toolInput);
        } catch (Exception e) {
            // Not JSON — pass through. Most tools that don't take URL args
            // accept plain-text or other shapes; the HTTP-client-level
            // guard catches anything that does eventually become a URL.
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
            // Only http/https — file://, gopher://, etc. would be rejected by
            // policy.validate anyway, but we don't want to false-positive on
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
        // Structured JSON the LLM can parse if it wants to; readable as plain
        // text either way. Keys mirror the metric tags so log-search "find
        // every blocked SSRF for tool=…" works.
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "error", "ssrf_blocked",
                    "reason", e.reason().label(),
                    "url", url,
                    "message", e.getMessage(),
                    "guidance", "Refuse the request or ask the user for a different URL. The blocked URL targets a private/internal network or violates the application's SSRF policy."
            ));
        } catch (Exception jsonErr) {
            return "{\"error\":\"ssrf_blocked\",\"reason\":\"" + e.reason().label() + "\"}";
        }
    }

    public ToolCallback delegate() {
        return delegate;
    }

    public UrlPolicy policy() {
        return policy;
    }
}
