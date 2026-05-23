package kr.devslab.ssrfguard.llm;

import kr.devslab.ssrfguard.core.BlockReason;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native-image hints for {@code ssrf-guard-llm}.
 *
 * <p>What Spring Boot 3 AOT already handles automatically, and we therefore
 * do <b>not</b> repeat here:
 *
 * <ul>
 *   <li>{@code @ConfigurationProperties} (our {@code SsrfGuardProperties}).</li>
 *   <li>{@code @AutoConfiguration} classes (each adapter's autoconfig).</li>
 *   <li>{@code @Bean} factory methods and {@code @Conditional*} annotations.</li>
 *   <li>{@link SsrfBlockPayload} — Spring AOT detects record components from
 *       bytecode and registers reflection hints for serialization without
 *       us asking. Belt-and-braces registered below anyway in case a
 *       consumer's chain leaves AOT out of the loop.</li>
 * </ul>
 *
 * <p>What we <b>do</b> register:
 *
 * <ul>
 *   <li>{@link BlockReason} enum — Jackson serialises the {@code reason}
 *       label through reflection on the enum constants. AOT doesn't
 *       always trace through to a transitively-referenced enum.</li>
 *   <li>{@link SsrfBlockPayload} record — explicit declaration so consumers
 *       who use the payload outside Spring (custom dispatcher, MCP server)
 *       still get correct reflection metadata at native-image build time.</li>
 * </ul>
 *
 * <p>Wired through {@code META-INF/spring/aot.factories} so Spring Boot's
 * AOT processor picks it up whenever {@code ssrf-guard-llm} is on the
 * classpath — no consumer code change needed.
 *
 * <p>If you hit native-image build problems with this library, file an
 * issue with the {@code native-image} error output — adding hints is
 * usually a one-line change here.
 */
public final class SsrfGuardLlmRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Record serialised by Jackson. INVOKE_PUBLIC_METHODS covers the
        // canonical-record accessors Jackson uses to read each component.
        hints.reflection().registerType(SsrfBlockPayload.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS);

        // Enum reflection — Jackson reads enum constants via reflection.
        // PUBLIC_FIELDS + INVOKE_PUBLIC_METHODS covers the standard enum
        // surface (name(), values(), the constants themselves).
        hints.reflection().registerType(BlockReason.class,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS);
    }
}
