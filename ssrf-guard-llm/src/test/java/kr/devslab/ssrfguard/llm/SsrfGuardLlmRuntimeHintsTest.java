package kr.devslab.ssrfguard.llm;

import kr.devslab.ssrfguard.core.BlockReason;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the hints {@link SsrfGuardLlmRuntimeHints} registers — running
 * this test without a GraalVM native image still confirms the
 * {@code RuntimeHintsRegistrar} adds the right metadata to the hints
 * snapshot, so a downstream {@code ./gradlew nativeBuild} won't miss our
 * reflective surface.
 */
class SsrfGuardLlmRuntimeHintsTest {

    @Test
    void registers_payload_record_for_reflective_serialization() {
        RuntimeHints hints = new RuntimeHints();
        new SsrfGuardLlmRuntimeHints().registerHints(hints, getClass().getClassLoader());

        // Jackson serialises records via INVOKE_PUBLIC_METHODS on the
        // accessor methods; AOT must allow that.
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(SsrfBlockPayload.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS))
                .accepts(hints);
    }

    @Test
    void registers_block_reason_enum() {
        RuntimeHints hints = new RuntimeHints();
        new SsrfGuardLlmRuntimeHints().registerHints(hints, getClass().getClassLoader());

        // BlockReason.label() is invoked at serialize time; reflection on
        // enum constants must be permitted.
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(BlockReason.class))
                .accepts(hints);
    }
}
