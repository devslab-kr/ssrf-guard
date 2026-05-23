package kr.devslab.ssrfguard.llm;

/**
 * The JSON shape returned to an LLM when a tool input fails the SSRF policy.
 * Used as the structured-error payload by {@link JsonToolInputGuard} and any
 * other adapter that wants to surface a uniform "I can't fetch that URL"
 * message to the model.
 *
 * <p>Why a record, not a {@code Map.of(...)}.
 * <ul>
 *   <li><b>GraalVM native-image:</b> a record's component metadata is part
 *       of the bytecode — Spring Boot's AOT processor (3.x+) registers
 *       reflection hints automatically. The previous {@code Map.of(...)}
 *       form used JDK {@code ImmutableCollections.MapN}, whose internals
 *       are private and inconsistent across JVMs, so AOT couldn't help
 *       and a downstream native-image build would either fail or strip
 *       the serialised fields.</li>
 *   <li><b>Stable wire shape:</b> field declaration order is the JSON
 *       output order (Jackson honours record component order). With
 *       {@code Map.of} the iteration order was officially undefined,
 *       so the JSON shape could drift between JVMs.</li>
 *   <li><b>Type safety:</b> the five payload fields are now visible to
 *       the compiler. Renaming one in a hurry no longer breaks the
 *       contract LLMs read on their next turn.</li>
 * </ul>
 *
 * <p>The JSON keys are part of the library's public contract — well-behaved
 * LLMs are prompted to read these field names (especially {@code error}
 * and {@code reason}) to decide how to recover. Don't rename.
 *
 * @param error     constant string {@code "ssrf_blocked"} — lets the LLM
 *                  distinguish an SSRF rejection from other tool errors
 * @param reason    stable {@link kr.devslab.ssrfguard.core.BlockReason#label()}
 *                  enum label — safe to use as a metric tag or log filter
 * @param url       the URL the policy rejected, copied verbatim from the tool input
 * @param message   the {@link kr.devslab.ssrfguard.core.SsrfGuardException}
 *                  message text — human-readable explanation of the gate that fired
 * @param guidance  short prompt fragment guiding the LLM on what to do next
 */
public record SsrfBlockPayload(
        String error,
        String reason,
        String url,
        String message,
        String guidance
) {

    /**
     * Convenience factory that supplies the constant {@code error} value
     * and the standard guidance text. Adapters call this instead of the
     * full constructor.
     */
    public static SsrfBlockPayload of(String reason, String url, String message) {
        return new SsrfBlockPayload(
                "ssrf_blocked",
                reason,
                url,
                message,
                "Refuse the request or ask the user for a different URL. " +
                        "The blocked URL targets a private/internal network or violates the application's SSRF policy."
        );
    }
}
