package kr.devslab.ssrfguard.springai;

import kr.devslab.ssrfguard.core.UrlPolicy;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Convenience helpers for bulk-wrapping Spring AI tool callbacks.
 *
 * <pre>{@code
 * ToolCallback[] raw = ToolCallbacks.from(new MyTools());
 * ToolCallback[] safe = SsrfGuardedToolCallbacks.wrap(raw, urlPolicy);
 *
 * ChatClient.create(chatModel)
 *     .prompt("Fetch the homepage of example.com")
 *     .toolCallbacks(safe)
 *     .call()
 *     .content();
 * }</pre>
 *
 * <p>The naming mirrors Spring AI's own {@code ToolCallbacks} utility, so
 * the wrap is a visible step in the chat-client wiring rather than a
 * silent post-processor.
 */
public final class SsrfGuardedToolCallbacks {
    private SsrfGuardedToolCallbacks() {}

    /** Wraps each callback, leaving any already-wrapped ones untouched. */
    public static ToolCallback[] wrap(ToolCallback[] callbacks, UrlPolicy policy) {
        if (callbacks == null) return new ToolCallback[0];
        ToolCallback[] out = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            out[i] = wrapOne(callbacks[i], policy);
        }
        return out;
    }

    /** Same but for a {@link Collection}. */
    public static List<ToolCallback> wrap(Collection<? extends ToolCallback> callbacks, UrlPolicy policy) {
        if (callbacks == null) return List.of();
        List<ToolCallback> out = new ArrayList<>(callbacks.size());
        for (ToolCallback cb : callbacks) out.add(wrapOne(cb, policy));
        return out;
    }

    /** Wrap a single callback (idempotent for already-wrapped ones). */
    public static ToolCallback wrapOne(ToolCallback cb, UrlPolicy policy) {
        if (cb == null) return null;
        if (cb instanceof SsrfGuardedToolCallback) return cb;
        return new SsrfGuardedToolCallback(cb, policy);
    }

    /** Convenience for varargs. */
    public static ToolCallback[] wrap(UrlPolicy policy, ToolCallback... callbacks) {
        return wrap(Arrays.copyOf(callbacks, callbacks.length), policy);
    }
}
