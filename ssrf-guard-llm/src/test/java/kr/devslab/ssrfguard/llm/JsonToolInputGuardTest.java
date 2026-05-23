package kr.devslab.ssrfguard.llm;

import kr.devslab.ssrfguard.core.BlockReason;
import kr.devslab.ssrfguard.core.HostPolicy;
import kr.devslab.ssrfguard.core.NoOpSsrfGuardMetrics;
import kr.devslab.ssrfguard.core.SsrfGuardException;
import kr.devslab.ssrfguard.core.UrlPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the framework-agnostic core of LLM tool input validation. These
 * tests intentionally do not touch any LLM framework — that's the whole
 * point of extracting this module.
 */
class JsonToolInputGuardTest {

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
    void returns_null_when_input_is_blank_or_not_json() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError(null)).isNull();
        assertThat(guard.checkOrFormatError("")).isNull();
        assertThat(guard.checkOrFormatError("   ")).isNull();
        assertThat(guard.checkOrFormatError("not json at all")).isNull();
    }

    @Test
    void allows_input_without_urls() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"query\":\"weather today\",\"limit\":5}")).isNull();
    }

    @Test
    void allows_whitelisted_url_in_top_level_field() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"url\":\"https://api.example.com/v1\"}")).isNull();
    }

    @Test
    void blocks_aws_metadata_url_returns_structured_error() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\"http://169.254.169.254/latest/meta-data/\"}");
        assertThat(err)
                .isNotNull()
                .contains("\"error\":\"ssrf_blocked\"")
                .contains("\"reason\":\"blocked_ip_literal\"")
                .contains("\"url\":\"http://169.254.169.254/latest/meta-data/\"")
                .contains("\"guidance\":");
    }

    @Test
    void blocks_nested_url_field() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"request\":{\"target\":\"https://evil.com/\"},\"timeout\":5}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void blocks_url_inside_array() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"urls\":[\"https://api.example.com/ok\", \"https://evil.com/bad\"]}");
        assertThat(err).contains("\"error\":\"ssrf_blocked\"");
    }

    @Test
    void blocks_obfuscated_ip_literal() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        String err = guard.checkOrFormatError("{\"url\":\"http://2130706433/\"}");
        assertThat(err).contains("\"reason\":\"blocked_ip_literal\"");
    }

    @Test
    void throws_when_throw_on_violation_is_true() {
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")), true);
        assertThatExceptionOfType(SsrfGuardException.class)
                .isThrownBy(() -> guard.checkOrFormatError("{\"url\":\"http://10.0.0.5/\"}"))
                .matches(e -> e.reason() == BlockReason.BLOCKED_IP_LITERAL);
    }

    @Test
    void ignores_non_http_schemes() {
        // mailto:, urn:uuid:, file:// — should not trip the URL detector
        // because looksLikeUrl() only matches http(s)://
        var guard = new JsonToolInputGuard(policy(List.of("api.example.com")));
        assertThat(guard.checkOrFormatError("{\"to\":\"mailto:user@example.com\"}")).isNull();
        assertThat(guard.checkOrFormatError("{\"id\":\"urn:uuid:abc\"}")).isNull();
    }
}
