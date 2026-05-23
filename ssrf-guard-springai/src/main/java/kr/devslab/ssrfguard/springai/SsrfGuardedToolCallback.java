package kr.devslab.ssrfguard.springai;

import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.llm.JsonToolInputGuard;
import kr.devslab.ssrfguard.llm.ToolInputGuard;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Wraps a Spring AI {@link ToolCallback} so URL-shaped tool inputs are
 * validated against an {@link UrlPolicy} before the underlying tool executes.
 *
 * <p><b>v3.1 note.</b> The JSON walking + URL extraction + policy validation
 * lives in {@link JsonToolInputGuard} (in the {@code ssrf-guard-llm} module).
 * This class is a 30-line adapter that translates between Spring AI's
 * {@code ToolCallback} interface and the framework-agnostic
 * {@link ToolInputGuard} contract. Same applies to
 * {@code SsrfGuardedTool} in {@code ssrf-guard-langchain4j} — the two
 * adapters share the same core, so a fix to URL detection or error
 * formatting lands in one place.
 *
 * <p>The public API is unchanged from v3.0.x — same constructors, same
 * behaviour. Existing consumers don't see the refactor.
 *
 * <h2>What this still does (delegated to the core)</h2>
 * <ul>
 *   <li>Parses the JSON tool input.</li>
 *   <li>Walks the entire tree (objects, arrays, nested) for {@code http(s)://}
 *       strings — naive top-level-only checks miss nested URLs and
 *       prompt-injected context strings.</li>
 *   <li>On rejection, returns a structured JSON error string the LLM can
 *       read on its next turn and recover from. The error shape is
 *       documented on {@link JsonToolInputGuard#checkOrFormatError(String)}.</li>
 *   <li>Or throws {@link SsrfGuardException} if {@code throwOnViolation} is
 *       set — fail-loud for CI / test contexts.</li>
 * </ul>
 */
public final class SsrfGuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolInputGuard guard;

    /**
     * @param delegate         the raw {@link ToolCallback} this wraps
     * @param policy           the URL policy applied to every URL in the tool input
     */
    public SsrfGuardedToolCallback(ToolCallback delegate, UrlPolicy policy) {
        this(delegate, policy, false);
    }

    /**
     * @param throwOnViolation when {@code true}, the wrap rethrows
     *                         {@link SsrfGuardException} on a violation instead
     *                         of returning a JSON error string. Useful for
     *                         CI and dev-mode harnesses.
     */
    public SsrfGuardedToolCallback(ToolCallback delegate, UrlPolicy policy, boolean throwOnViolation) {
        this.delegate = delegate;
        this.guard = new JsonToolInputGuard(policy, throwOnViolation);
    }

    /**
     * Advanced constructor — supply a custom {@link ToolInputGuard}. Useful
     * if you want non-JSON tool input handling, a different error payload
     * shape, or to plug in a fixed-decision guard for tests.
     */
    public SsrfGuardedToolCallback(ToolCallback delegate, ToolInputGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
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
        String violation = guard.checkOrFormatError(toolInput);
        return violation != null ? violation : delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String violation = guard.checkOrFormatError(toolInput);
        return violation != null ? violation : delegate.call(toolInput, toolContext);
    }

    public ToolCallback delegate() {
        return delegate;
    }

    public ToolInputGuard guard() {
        return guard;
    }
}
