package kr.devslab.ssrfguard.springai;

import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SsrfGuardedToolCallbackTest {

    /**
     * The threat model under test: an LLM agent invokes a "fetch_url" tool
     * with attacker-controlled URLs. The wrap should block private/loopback
     * targets before the underlying tool's HTTP call runs.
     */
    static class FakeFetchUrlTool implements ToolCallback {
        final AtomicInteger callCount = new AtomicInteger();
        @Override public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                    .name("fetch_url")
                    .description("Fetch a URL")
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}")
                    .build();
        }
        @Override public ToolMetadata getToolMetadata() {
            return DefaultToolMetadata.builder().build();
        }
        @Override public String call(String toolInput) {
            callCount.incrementAndGet();
            return "OK fetched";
        }
        @Override public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

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

    @Test
    void permits_whitelisted_url() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        String result = safe.call("{\"url\":\"https://api.example.com/page\"}");
        assertThat(result).isEqualTo("OK fetched");
        assertThat(tool.callCount).hasValue(1);
    }

    @Test
    void blocks_aws_metadata_url_default_returns_error_string() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        String result = safe.call("{\"url\":\"http://169.254.169.254/latest/meta-data/iam/security-credentials/\"}");
        assertThat(result)
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":");
        assertThat(tool.callCount)
                .as("underlying tool must NOT have been invoked")
                .hasValue(0);
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        String result = safe.call("{\"url\":\"http://2130706433/\"}");
        assertThat(result).contains("\"reason\":\"blocked_ip_literal\"");
        assertThat(tool.callCount).hasValue(0);
    }

    @Test
    void blocks_nested_url_field() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        // Attacker hides the URL inside a "context" object — recursive walk must find it.
        String result = safe.call("{\"request\":{\"target\":\"https://evil.com/\"},\"timeout\":5}");
        assertThat(result).contains("\"error\":\"ssrf_blocked\"");
        assertThat(tool.callCount).hasValue(0);
    }

    @Test
    void blocks_url_inside_array() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        String result = safe.call("{\"urls\":[\"https://api.example.com/ok\", \"https://evil.com/bad\"]}");
        assertThat(result).contains("\"error\":\"ssrf_blocked\"");
        assertThat(tool.callCount).hasValue(0);
    }

    @Test
    void throws_when_throw_on_violation_is_true() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(
                tool, policy(List.of("api.example.com")), true);
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> safe.call("{\"url\":\"http://10.0.0.5/\"}"));
        assertThat(tool.callCount).hasValue(0);
    }

    @Test
    void passes_through_non_json_input() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        // Some tools take plain text input (not JSON). We must not break those.
        String result = safe.call("just a plain string with no URL");
        assertThat(result).isEqualTo("OK fetched");
    }

    @Test
    void passes_through_tool_input_without_urls() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        String result = safe.call("{\"query\":\"weather today\",\"limit\":5}");
        assertThat(result).isEqualTo("OK fetched");
    }

    @Test
    void delegates_definition_and_metadata() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        SsrfGuardedToolCallback safe = new SsrfGuardedToolCallback(tool, policy(List.of("api.example.com")));
        assertThat(safe.getToolDefinition().name()).isEqualTo("fetch_url");
        assertThat(safe.getToolMetadata()).isNotNull();
    }

    @Test
    void wrap_helper_is_idempotent() {
        FakeFetchUrlTool tool = new FakeFetchUrlTool();
        ToolCallback once = SsrfGuardedToolCallbacks.wrapOne(tool, policy(List.of("api.example.com")));
        ToolCallback twice = SsrfGuardedToolCallbacks.wrapOne(once, policy(List.of("api.example.com")));
        assertThat(twice).isSameAs(once);
    }
}
