package kr.devslab.ssrfguard.llm;

import kr.devslab.ssrfguard.core.SsrfGuardException;

/**
 * Validates URL-shaped arguments inside LLM tool inputs. Framework-agnostic —
 * Spring AI, LangChain4j, custom dispatchers, MCP servers, and anyone else
 * who hands a JSON blob to an underlying tool can route that blob through a
 * single {@link ToolInputGuard} implementation before letting the tool run.
 *
 * <p>The contract on the return value is deliberately simple — the caller
 * either gets {@code null} (everything OK, proceed) or a JSON-shaped error
 * string ready to be returned to the LLM verbatim:
 *
 * <pre>{@code
 * {"error":"ssrf_blocked","reason":"blocked_private_ip","url":"http://169.254.169.254/...","message":"...","guidance":"..."}
 * }</pre>
 *
 * <p>That payload is what well-behaved chat models read on their next turn
 * and use to apologise / ask for a different URL rather than crashing the
 * agent loop with an unhandled exception. Implementations that prefer
 * fail-loud semantics (CI tests, dev-mode harnesses) can throw
 * {@link SsrfGuardException} instead — see {@link JsonToolInputGuard}'s
 * {@code throwOnViolation} flag.
 *
 * <h2>Why an interface rather than a single concrete type</h2>
 * Most adapters take the default {@link JsonToolInputGuard} and depend on
 * the interface. Two reasons we still expose the abstraction:
 *
 * <ul>
 *   <li>Some tool frameworks expect the input as a {@code Map}, a list of
 *       {@code ToolCallArgument} records, or a binary protobuf. Custom
 *       implementations can adapt those shapes to the same return-string
 *       contract without re-implementing URL extraction.</li>
 *   <li>Tests can swap in a no-op or a fixed-decision guard without standing
 *       up the JSON walker.</li>
 * </ul>
 */
public interface ToolInputGuard {

    /**
     * Walk the tool input, find every URL-shaped argument, and validate
     * each through the configured policy.
     *
     * @param toolInput the raw input the framework would hand to the tool —
     *                  typically a JSON object string. May be null or blank.
     * @return {@code null} if every URL passed; a JSON error payload string
     *         if any URL was blocked. Concrete shape documented on
     *         {@link JsonToolInputGuard#checkOrFormatError(String)}.
     * @throws SsrfGuardException if the implementation is configured to
     *                            throw on violations (see
     *                            {@link JsonToolInputGuard}'s
     *                            {@code throwOnViolation} flag) and a URL
     *                            failed the policy.
     */
    String checkOrFormatError(String toolInput);
}
