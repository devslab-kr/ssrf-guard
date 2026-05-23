package kr.devslab.ssrfguard.langchain4j;

import dev.langchain4j.service.tool.ToolExecutor;
import kr.devslab.ssrfguard.core.UrlPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Convenience helpers for bulk-wrapping LangChain4j tool executors.
 *
 * <pre>{@code
 * Map<ToolSpecification, ToolExecutor> raw = ...;
 * Map<ToolSpecification, ToolExecutor> safe = SsrfGuardedToolExecutors.wrap(raw, urlPolicy);
 *
 * AiServices.builder(MyAssistant.class)
 *     .chatModel(model)
 *     .tools(safe)
 *     .build();
 * }</pre>
 *
 * <p>The Spring auto-config wraps {@code ToolExecutor} beans via a
 * {@code BeanPostProcessor}; these helpers are for the non-Spring case
 * (plain LangChain4j) and the "I know exactly which executors I want
 * wrapped" case.
 */
public final class SsrfGuardedToolExecutors {
    private SsrfGuardedToolExecutors() {}

    /** Wrap a single executor. Idempotent — already-wrapped passes through. */
    public static ToolExecutor wrapOne(ToolExecutor executor, UrlPolicy policy) {
        if (executor == null) return null;
        if (executor instanceof SsrfGuardedToolExecutor) return executor;
        return new SsrfGuardedToolExecutor(executor, policy);
    }

    /** Wrap a collection. Preserves order. */
    public static List<ToolExecutor> wrap(Collection<? extends ToolExecutor> executors, UrlPolicy policy) {
        if (executors == null) return List.of();
        List<ToolExecutor> out = new ArrayList<>(executors.size());
        for (ToolExecutor e : executors) out.add(wrapOne(e, policy));
        return out;
    }

    /**
     * Wrap a {@code Map<ToolSpecification, ToolExecutor>} — the shape
     * {@code AiServices.builder(...).tools(...)} expects when registering
     * tools programmatically (instead of via {@code @Tool} annotations).
     * Keys (specs) are returned unchanged; values are wrapped.
     */
    public static <K> Map<K, ToolExecutor> wrap(Map<K, ToolExecutor> specToExecutor, UrlPolicy policy) {
        if (specToExecutor == null) return Map.of();
        java.util.LinkedHashMap<K, ToolExecutor> out = new java.util.LinkedHashMap<>(specToExecutor.size());
        for (var entry : specToExecutor.entrySet()) {
            out.put(entry.getKey(), wrapOne(entry.getValue(), policy));
        }
        return out;
    }
}
