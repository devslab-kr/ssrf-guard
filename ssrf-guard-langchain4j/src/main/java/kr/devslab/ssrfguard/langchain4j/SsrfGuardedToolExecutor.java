package kr.devslab.ssrfguard.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.llm.JsonToolInputGuard;
import kr.devslab.ssrfguard.llm.ToolInputGuard;

/**
 * Wraps a LangChain4j {@link ToolExecutor} so URL-shaped arguments in the
 * incoming {@link ToolExecutionRequest} are validated against an
 * {@link UrlPolicy} before the underlying executor runs.
 *
 * <p>Sibling to {@code SsrfGuardedToolCallback} in {@code ssrf-guard-springai}.
 * Same model, same core ({@link JsonToolInputGuard}) — only the framework
 * abstraction differs.
 *
 * <h2>The threat — recap</h2>
 * LangChain4j {@code @Tool} methods and programmatic {@code ToolExecutor}s
 * both end up receiving a JSON {@code arguments} string from the LLM. A
 * tool like:
 * <pre>{@code
 * @Tool("Fetch a URL and return its body")
 * String fetchUrl(String url) {
 *     return restClient.get().uri(url).retrieve().body(String.class);
 * }
 * }</pre>
 * is one prompt away from SSRF if {@code url} is attacker-controlled
 * (directly via user message, or indirectly via RAG-injected instructions).
 *
 * <h2>What the wrap does</h2>
 * <ol>
 *   <li>Reads {@code request.arguments()} — the JSON the LLM emitted.</li>
 *   <li>Delegates to {@link JsonToolInputGuard} — walks the whole tree, finds
 *       URL-shaped strings, validates each through {@link UrlPolicy}.</li>
 *   <li>On rejection, returns the structured JSON error directly (the LLM
 *       reads it on its next turn and recovers). On approval, delegates to
 *       the wrapped executor.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * Manual wrap (works without Spring):
 * <pre>{@code
 * UrlPolicy policy = ...;
 * ToolExecutor raw = (request, memoryId) -> myFetchUrl(request.arguments());
 * ToolExecutor safe = new SsrfGuardedToolExecutor(raw, policy);
 *
 * AiServices.builder(MyAssistant.class)
 *     .chatModel(model)
 *     .tools(Map.of(toolSpec, safe))
 *     .build();
 * }</pre>
 *
 * Auto-wrap (Spring): drop {@code ssrf-guard-langchain4j} on the classpath —
 * {@link SsrfGuardLangchain4jAutoConfiguration} registers a
 * {@code BeanPostProcessor} that wraps every {@link ToolExecutor} bean in
 * the context.
 */
public final class SsrfGuardedToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final ToolInputGuard guard;

    public SsrfGuardedToolExecutor(ToolExecutor delegate, UrlPolicy policy) {
        this(delegate, policy, false);
    }

    public SsrfGuardedToolExecutor(ToolExecutor delegate, UrlPolicy policy, boolean throwOnViolation) {
        this.delegate = delegate;
        this.guard = new JsonToolInputGuard(policy, throwOnViolation);
    }

    /**
     * Advanced constructor — supply a custom {@link ToolInputGuard}. Useful
     * for non-JSON tool argument shapes, custom error payloads, or
     * fixed-decision guards in tests.
     */
    public SsrfGuardedToolExecutor(ToolExecutor delegate, ToolInputGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        String violation = guard.checkOrFormatError(request.arguments());
        return violation != null ? violation : delegate.execute(request, memoryId);
    }

    public ToolExecutor delegate() {
        return delegate;
    }

    public ToolInputGuard guard() {
        return guard;
    }
}
