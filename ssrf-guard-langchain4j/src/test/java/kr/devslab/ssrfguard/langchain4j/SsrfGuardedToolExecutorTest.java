package kr.devslab.ssrfguard.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the LangChain4j adapter against the same threat model as Spring AI:
 * the LLM hands over a {@code ToolExecutionRequest} with a URL in the
 * {@code arguments} JSON, the wrap inspects it through {@code UrlPolicy},
 * and the underlying executor never runs if the URL is rejected.
 */
class SsrfGuardedToolExecutorTest {

    private static UrlPolicy policy(List<String> exact) {
        return new UrlPolicy(
                Set.of("http", "https"),
                Set.of(-1, 80, 443),
                new HostPolicy(exact, List.of()),
                true,
                true,
                NoOpSsrfGuardMetrics.INSTANCE
        );
    }

    private static ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build();
    }

    /** A fake fetch_url tool that counts invocations so we can assert the wrap blocks. */
    static class CountingFetchUrlTool implements ToolExecutor {
        final AtomicInteger invocations = new AtomicInteger();

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            invocations.incrementAndGet();
            return "PRETEND-FETCHED " + request.arguments();
        }
    }

    @Test
    void permits_whitelisted_url() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")));

        String result = safe.execute(request("fetch_url", "{\"url\":\"https://api.example.com/v1\"}"), null);

        assertThat(result).startsWith("PRETEND-FETCHED");
        assertThat(tool.invocations).hasValue(1);
    }

    @Test
    void blocks_aws_metadata_url_returns_structured_error() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")));

        String result = safe.execute(
                request("fetch_url", "{\"url\":\"http://169.254.169.254/latest/meta-data/iam/security-credentials/\"}"),
                null);

        assertThat(result)
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":\"blocked_ip_literal\"");
        assertThat(tool.invocations)
                .as("underlying tool must NOT have been invoked")
                .hasValue(0);
    }

    @Test
    void blocks_nested_url_field() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")));

        String result = safe.execute(
                request("fetch_url", "{\"request\":{\"target\":\"https://evil.com/\"}}"),
                null);

        assertThat(result).contains("\"error\":\"ssrf_blocked\"");
        assertThat(tool.invocations).hasValue(0);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")));

        String result = safe.execute(request("fetch_url", "{\"url\":\"http://2130706433/\"}"), null);

        assertThat(result).contains("\"reason\":\"blocked_ip_literal\"");
        assertThat(tool.invocations).hasValue(0);
    }

    @Test
    void throws_when_throw_on_violation_is_true() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")), true);

        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> safe.execute(request("fetch_url", "{\"url\":\"http://10.0.0.5/\"}"), null));
        assertThat(tool.invocations).hasValue(0);
    }

    @Test
    void passes_through_non_json_input() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        SsrfGuardedToolExecutor safe = new SsrfGuardedToolExecutor(tool, policy(List.of("api.example.com")));

        // Some LangChain4j tools take plain-text args; non-JSON should pass through.
        String result = safe.execute(request("translate", "just a plain string"), null);

        assertThat(result).startsWith("PRETEND-FETCHED");
        assertThat(tool.invocations).hasValue(1);
    }

    @Test
    void wrapOne_is_idempotent() {
        CountingFetchUrlTool tool = new CountingFetchUrlTool();
        ToolExecutor once = SsrfGuardedToolExecutors.wrapOne(tool, policy(List.of("api.example.com")));
        ToolExecutor twice = SsrfGuardedToolExecutors.wrapOne(once, policy(List.of("api.example.com")));
        assertThat(twice).isSameAs(once);
    }
}
