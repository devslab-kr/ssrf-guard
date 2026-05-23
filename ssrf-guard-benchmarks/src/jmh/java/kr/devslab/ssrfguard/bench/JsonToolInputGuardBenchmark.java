package kr.devslab.ssrfguard.bench;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.UrlPolicy;
import kr.devslab.ssrfguard.llm.JsonToolInputGuard;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures the cost of {@code JsonToolInputGuard.checkOrFormatError(input)}
 * — what the {@code ssrf-guard-springai} and {@code ssrf-guard-langchain4j}
 * adapters call once per LLM tool invocation. The guard:
 *
 * <ol>
 *   <li>Parses the JSON tool-input string (Jackson)</li>
 *   <li>Walks the JSON tree</li>
 *   <li>For every string value that "looks like a URL", runs {@code UrlPolicy.check}</li>
 *   <li>On rejection, returns a formatted {@code SsrfBlockPayload} JSON string</li>
 * </ol>
 *
 * <p>Three payload shapes cover the realistic spread:
 *
 * <ul>
 *   <li><b>small_allowed</b> — `{"url":"https://api.partner.com/..."}`. Single
 *       URL, top-level, allowed. The minimum cost a `fetch_url` tool pays.</li>
 *   <li><b>medium_blocked</b> — nested object with a blocked URL two levels
 *       deep. Exercises the tree walk + the block / format-error path.</li>
 *   <li><b>large_allowed</b> — ~2KB JSON with many fields and 3 URLs. Closer
 *       to the shape of a real RAG-augmented tool input.</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class JsonToolInputGuardBenchmark {

    private JsonToolInputGuard guard;

    private static final String SMALL_ALLOWED =
            "{\"url\":\"https://api.partner.com/v1/users/42\"}";

    private static final String MEDIUM_BLOCKED =
            "{\"request\":{\"endpoint\":\"http://169.254.169.254/latest/meta-data/\","
            + "\"method\":\"GET\"},\"meta\":{\"reason\":\"audit\"}}";

    private static final String LARGE_ALLOWED;

    static {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"primary\":\"https://api.partner.com/v1/orders\",")
          .append("\"fallback\":\"https://httpbin.org/get\",")
          .append("\"webhook\":\"https://api.partner.com/v1/events\",")
          .append("\"meta\":{");
        for (int i = 0; i < 40; i++) {
            if (i > 0) sb.append(',');
            sb.append("\"field").append(i).append("\":\"value").append(i).append('"');
        }
        sb.append("}}");
        LARGE_ALLOWED = sb.toString();
    }

    @Setup
    public void setup() {
        HostPolicy hosts = new HostPolicy(List.of("api.partner.com", "httpbin.org"), List.of());
        UrlPolicy policy = new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                hosts,
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE);
        guard = new JsonToolInputGuard(policy, /* throwOnViolation */ false);
    }

    @Benchmark
    public void small_allowed(Blackhole bh) {
        // The cheapest realistic LLM-tool input: one URL, top-level.
        bh.consume(guard.checkOrFormatError(SMALL_ALLOWED));
    }

    @Benchmark
    public void medium_blocked(Blackhole bh) {
        // Nested URL, blocked at the IP-literal check. Exercises both the
        // tree walk and the SsrfBlockPayload JSON formatting.
        bh.consume(guard.checkOrFormatError(MEDIUM_BLOCKED));
    }

    @Benchmark
    public void large_allowed(Blackhole bh) {
        // ~2KB JSON with 3 URLs + 40 non-URL fields — closer to a RAG-style
        // tool input.
        bh.consume(guard.checkOrFormatError(LARGE_ALLOWED));
    }
}
